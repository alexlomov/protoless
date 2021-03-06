package io.protoless.messages

import scala.annotation.implicitNotFound
import scala.util.{Failure, Success, Try}
import java.io.InputStream
import java.nio.ByteBuffer

import com.google.protobuf.{CodedInputStream => CIS}

import io.protoless.error.DecodingFailure
import io.protoless.messages.Decoder.Result
import io.protoless.messages.streams.ProtolessInputStream

/**
  * Interface for all Decoder implementations.
  *
  * Allows to decode protobuf3 serialized message in type `A`. If the message is not compatible with `A`,
  * return a [[io.protoless.error.DecodingFailure]].
  *
  * Decoding can be done with `Automatic` strategy with [[decoders.AutoDecoder]], or by specifying
  * a custom protobuf mapping with [[decoders.CustomMappingDecoder]].
  */
@implicitNotFound("No Decoder found for type ${A}.")
trait Decoder[A] extends Serializable { self =>

  /**
    * Try to read the value `A` in the CodedInputStream.
    */
  def decode(input: CIS): Result[A]

  /**
    * Try to read the value `A` from the Array[Byte].
    */
  def decode(input: Array[Byte]): Result[A] = decode(CIS.newInstance(input))

  /**
    * Try to read the value `A` from the InputStream.
    */
  def decode(input: InputStream): Result[A] = decode(CIS.newInstance(input))

  /**
    * Try to read the value `A` from the ByteBuffer.
    */
  def decode(input: ByteBuffer): Result[A] = decode(CIS.newInstance(input))

  /**
    * Run two decoders and return their results as a pair.
    */
  final def and[B](fb: Decoder[B]): Decoder[(A, B)] = new Decoder[(A, B)] {
    final def decode(input: CIS): Result[(A, B)] = for {
      a <- self.decode(input).right
      b <- fb.decode(input).right
    } yield (a, b)
  }

  /**
    * Choose the first succeeding decoder.
    */
  final def or[AA >: A](d: => Decoder[AA]): Decoder[AA] = new Decoder[AA] {
    final def decode(input: CIS): Result[AA] = self.decode(input) match {
      case r @ Right(_) => r
      case Left(_) => d.decode(input)
    }
  }

  /**
    * Create a new decoder that performs some operation on the result if this one succeeds.
    *
    * @param f a function returning either a value or an error message
    */
  final def emap[B](f: A => Either[DecodingFailure, B]): Decoder[B] = new Decoder[B] {
    final def decode(input: CIS): Result[B] = {
      for {
        a <- self.decode(input).right
        b <- f(a).right
      } yield b
    }
  }

  /**
    * Create a new decoder that performs some operation on the result if this one succeeds.
    *
    * @param f a function returning either a value or an error message
    */
  final def emapTry[B](f: A => Try[B]): Decoder[B] = new Decoder[B] {
    final def decode(input: CIS): Result[B] = {
      for {
        a <- self.decode(input).right
        b <- (f(a) match {
          case Failure(ex) => Left(DecodingFailure.fromThrowable(ex, 0))
          case Success(v) => Right(v)
        }).right
      } yield b
    }
  }
}

/**
  * Utilities for [[Decoder]]
  */
object Decoder {

  /**
    * Result type of the decoding process.
    */
  type Result[A] = Either[DecodingFailure, A]

  /**
    * Summon a decoder for type `A`.
    */
  def apply[A](implicit decoder: Decoder[A]): Decoder[A] = decoder

  /**
    * Construct an instance from a function.
    *
    * @param f how to read the object `A` from the `CodedInputStream`.
    */
  def instance[A](f: ProtolessInputStream => Decoder.Result[A]): Decoder[A] = new Decoder[A] {
    override def decode(input: CIS): Result[A] = f(new ProtolessInputStream(input))
  }

  /**
    * Create a decoder that always returns a single value.
    */
  def const[A](a: A): Decoder[A] = instance(_ => Right(a))

  /**
    * Create a decoder that always return a failure.
    */
  def failed[A](failure: String): Decoder[A] = instance(_ => Left(DecodingFailure.apply(failure)))

}




