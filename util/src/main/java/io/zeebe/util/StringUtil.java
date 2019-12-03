/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class StringUtil {

  public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

  public static byte[] getBytes(final String value) {
    return getBytes(value, DEFAULT_CHARSET);
  }

  public static byte[] getBytes(final String value, final Charset charset) {
    return value.getBytes(charset);
  }

  public static String fromBytes(final byte[] bytes) {
    return fromBytes(bytes, DEFAULT_CHARSET);
  }

  public static String fromBytes(final byte[] bytes, final Charset charset) {
    return new String(bytes, charset);
  }

  public static String limitString(final String message, int maxLength) {
    if (message.length() > maxLength) {
      return message.substring(0, maxLength).concat("...");
    } else {
      return message;
    }
  }
}
