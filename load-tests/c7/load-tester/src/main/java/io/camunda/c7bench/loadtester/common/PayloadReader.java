/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.loadtester.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.camunda.bpm.client.variable.ClientValues;

/**
 * Converts a flat JSON payload (same files as the C8 load tester) into C7 variables.
 *
 * <p>Top-level keys become process variables. Primitives keep their type; objects and arrays
 * become {@code Json} variables (Camunda Spin), the usual way to store structured data in C7.
 */
public final class PayloadReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PayloadReader() {}

  public static String readResource(final String path) {
    try (InputStream in = open(path)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Cannot read " + path, e);
    }
  }

  /** Classpath resource first, then file system (allows mounting custom payloads). */
  private static InputStream open(final String path) throws IOException {
    final InputStream cp = PayloadReader.class.getClassLoader().getResourceAsStream(path);
    return cp != null ? cp : Files.newInputStream(Path.of(path));
  }

  /** Variables in the REST API format: {"name": {"value": ..., "type": ...}}. */
  public static ObjectNode toRestVariables(final String json) {
    final ObjectNode result = MAPPER.createObjectNode();
    parse(json)
        .properties()
        .forEach(
            e -> {
              final JsonNode v = e.getValue();
              final ObjectNode var = result.putObject(e.getKey());
              if (v.isContainerNode()) {
                var.put("value", v.toString());
                var.put("type", "Json");
              } else {
                var.set("value", v);
                var.put("type", restType(v));
              }
            });
    return result;
  }

  /** Variables for the external task client. */
  public static Map<String, Object> toClientVariables(final String json) {
    final Map<String, Object> result = new LinkedHashMap<>();
    parse(json)
        .properties()
        .forEach(
            e -> {
              final JsonNode v = e.getValue();
              final Object value;
              if (v.isContainerNode()) {
                value = ClientValues.jsonValue(v.toString());
              } else if (v.isTextual()) {
                value = v.textValue();
              } else if (v.isBoolean()) {
                value = v.booleanValue();
              } else if (v.isInt()) {
                value = v.intValue();
              } else if (v.isIntegralNumber()) {
                value = v.longValue();
              } else if (v.isNumber()) {
                value = v.doubleValue();
              } else {
                value = null;
              }
              result.put(e.getKey(), value);
            });
    return result;
  }

  private static String restType(final JsonNode v) {
    if (v.isTextual()) {
      return "String";
    } else if (v.isBoolean()) {
      return "Boolean";
    } else if (v.isInt()) {
      return "Integer";
    } else if (v.isIntegralNumber()) {
      return "Long";
    } else if (v.isNumber()) {
      return "Double";
    }
    return "Null";
  }

  private static JsonNode parse(final String json) {
    try {
      final JsonNode node = MAPPER.readTree(json);
      if (!node.isObject()) {
        throw new IllegalArgumentException("Payload must be a JSON object");
      }
      return node;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static ObjectMapper mapper() {
    return MAPPER;
  }
}
