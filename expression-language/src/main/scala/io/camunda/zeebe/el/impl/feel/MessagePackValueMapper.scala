/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el.impl.feel

import io.zeebe.el.impl.Loggers.LOGGER
import io.zeebe.msgpack.spec.{MsgPackReader, MsgPackToken, MsgPackType}
import io.zeebe.util.buffer.BufferUtil.bufferAsString
import org.agrona.DirectBuffer
import org.camunda.feel.syntaxtree.{Val, _}
import org.camunda.feel.valuemapper.CustomValueMapper

class MessagePackValueMapper extends CustomValueMapper {

  private val reader = new MsgPackReader

  override def toVal(x: Any, innerValueMapper: Any => Val): Option[Val] = x match {
    case messagePack: DirectBuffer => {
      val value = readMessagePack(messagePack)
      Some(value)
    }
    case _ => None
  }

  private def readMessagePack(messagePack: DirectBuffer): Val = {
    reader.wrap(messagePack, 0, messagePack.capacity())
    readNext()
  }

  private def readNext(): Val = {
    val offset = reader.getOffset
    val token = reader.readToken()

    read(token, offset)
  }

  private def read(token: MsgPackToken, offset: Int): Val = {
    token.getType match {
      case MsgPackType.NIL => ValNull
      case MsgPackType.BOOLEAN => ValBoolean(token.getBooleanValue)
      case MsgPackType.INTEGER => ValNumber(token.getIntegerValue)
      case MsgPackType.FLOAT => ValNumber(token.getFloatValue)
      case MsgPackType.STRING => {
        val asString = bufferAsString(token.getValueBuffer)
        ValString(asString)
      }
      case MsgPackType.ARRAY => {
        val items = (0 until token.getSize)
          .map(_ => readNext())
          .toList

        ValList(items)
      }
      case MsgPackType.MAP => {
        val context = new MessagePackContext(
          reader = reader,
          bufferOffset = offset,
          size = token.getSize
        )

        ValContext(context)
      }
      case other => {
        LOGGER.warn("No MessagePack to FEEL transformation for type '{}'. Using 'null' instead.", other)
        ValNull
      }
    }
  }

  // return the value as it is to not lose the type information
  override def unpackVal(value: Val, innerValueMapper: Val => Any): Option[Any] = Some(value)

}
