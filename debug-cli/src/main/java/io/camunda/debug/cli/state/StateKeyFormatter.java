/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbByte;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import java.util.HexFormat;
import org.agrona.concurrent.UnsafeBuffer;

@FunctionalInterface
interface StateKeyFormatter {

  String format(byte[] key);

  static StateKeyFormatter hexadecimal() {
    return key ->
        HexFormat.ofDelimiter(" ")
            .formatHex(key, key.length >= Long.BYTES ? Long.BYTES : 0, key.length);
  }

  static StateKeyFormatter databaseValues(final String format) {
    final var values = new DbValue[format.length()];
    for (int index = 0; index < format.length(); index++) {
      values[index] =
          switch (format.charAt(index)) {
            case 's' -> new DbString();
            case 'l' -> new DbLong();
            case 'i' -> new DbInt();
            case 'b' -> new DbByte();
            case 'B' -> new DbBytes();
            default ->
                throw new IllegalArgumentException(
                    "Unknown key format component: " + format.charAt(index));
          };
    }

    return key -> {
      final var formatted = new StringBuilder();
      final var keyBuffer = new UnsafeBuffer(key);
      var offset = Long.BYTES;
      try {
        for (final var value : values) {
          value.wrap(keyBuffer, offset, key.length - offset);
          offset += value.getLength();
          if (!formatted.isEmpty()) {
            formatted.append(':');
          }
          switch (value) {
            case DbString string -> formatted.append(string);
            case DbLong number -> formatted.append(number.getValue());
            case DbInt number -> formatted.append(number.getValue());
            case DbByte number -> formatted.append(number.getValue());
            case DbBytes bytes -> {
              final var rawBytes = new byte[bytes.getLength()];
              bytes.getDirectBuffer().getBytes(0, rawBytes);
              formatted.append(HexFormat.ofDelimiter(" ").formatHex(rawBytes));
            }
            default -> formatted.append(value);
          }
        }
        if (offset != key.length) {
          return hexadecimal().format(key);
        }
        return formatted.toString();
      } catch (final RuntimeException e) {
        return hexadecimal().format(key);
      }
    };
  }
}
