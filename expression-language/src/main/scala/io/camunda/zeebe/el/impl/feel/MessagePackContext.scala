/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.el.impl.feel

import io.camunda.zeebe.msgpack.spec.MsgPackReader
import io.camunda.zeebe.util.buffer.BufferUtil.{bufferAsString, cloneBuffer}
import org.agrona.DirectBuffer
import org.agrona.concurrent.UnsafeBuffer
import org.camunda.feel.context.{CustomContext, VariableProvider}

class MessagePackContext(
                          reader: MsgPackReader,
                          bufferOffset: Int,
                          size: Int
                        ) extends CustomContext {

  private val valueOffsets: Map[String, (Int, Int)] = readValueOffsets(reader, size)
  private val length = reader.getOffset - bufferOffset

  val messagePackMap: DirectBuffer = cloneBuffer(reader.getBuffer, bufferOffset, length)

  override val variableProvider: VariableProvider = new MessagePackMapVariableProvider(messagePackMap)

  class MessagePackMapVariableProvider(entries: DirectBuffer) extends VariableProvider {

    private val resultView = new UnsafeBuffer

    override def keys: Iterable[String] = valueOffsets.keys

    override def getVariable(name: String): Option[Any] = {
      valueOffsets
        .get(name)
        .map { case (offset, length) =>
          resultView.wrap(entries, offset, length)
          resultView
        }
    }

    override def getVariables: Map[String, Any] = valueOffsets.map { case (key, (offset, length)) =>
      key -> cloneBuffer(entries, offset, length)
    }

  }

  private def readValueOffsets(reader: MsgPackReader, size: Int): Map[String, (Int, Int)] = {
    val offsets = (0 until size).map { _ =>

      val keyToken = reader.readToken()
      val keyBuffer = keyToken.getValueBuffer
      val key = bufferAsString(keyBuffer)

      val valueOffset = reader.getOffset
      reader.skipValue()
      val valueLength = reader.getOffset - valueOffset

      key -> (valueOffset - bufferOffset, valueLength)
    }

    offsets.toMap
  }

}
