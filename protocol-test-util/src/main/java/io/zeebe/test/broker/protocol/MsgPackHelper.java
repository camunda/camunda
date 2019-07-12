/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MsgPackHelper {
  protected ObjectMapper objectMapper;

  public MsgPackHelper() {
    this.objectMapper = new ObjectMapper(new MessagePackFactory());
    // in the default setting, jackson deserializes numbers as Integer/Long/BigDecimal
    // depending on the value range; with that setting, asserting code has to do type conversion;
    // => we ensure it is always Long
    this.objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> readMsgPack(InputStream is) {
    try {
      return objectMapper.readValue(is, Map.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] encodeAsMsgPack(Object command) {
    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      objectMapper.writer().writeValue(byteArrayOutputStream, command);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return byteArrayOutputStream.toByteArray();
  }
}
