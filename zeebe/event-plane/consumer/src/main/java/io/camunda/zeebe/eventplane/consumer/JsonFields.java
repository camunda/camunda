/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A JSON object of the store, read field by field: a field that is missing or of another type is an
 * {@link InvalidObjectJsonException} that names it. Fields that aren't asked for are skipped, so
 * readers skip what later writers add.
 */
final class JsonFields {

  static final ObjectMapper MAPPER = new ObjectMapper();

  /** The version of every object this module reads and writes. */
  static final int VERSION = 1;

  private static final Pattern UUID_TEXT =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final int MAX_SHOWN = 200;

  private final JsonNode node;
  private final String path;

  private JsonFields(final JsonNode node, final String path) {
    this.node = node;
    this.path = path;
  }

  /**
   * Reads a document that must be a JSON object of version {@value #VERSION}.
   *
   * @throws InvalidObjectJsonException if it isn't
   * @throws IOException if the bytes aren't JSON
   */
  static JsonFields parse(final byte[] json) throws IOException {
    final var root = MAPPER.readTree(json);
    if (root == null || !root.isObject()) {
      throw new InvalidObjectJsonException("the document", "a JSON object", shown(json));
    }
    final var fields = new JsonFields(root, "");
    final long version = fields.longValue("version");
    if (version != VERSION) {
      throw new InvalidObjectJsonException("version", String.valueOf(VERSION), shown(version));
    }
    return fields;
  }

  /** A new object with the version first. */
  static ObjectNode versioned() {
    return MAPPER.createObjectNode().put("version", VERSION);
  }

  static byte[] write(final ObjectNode object) {
    try {
      return MAPPER.writeValueAsBytes(object);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  String text(final String field) throws InvalidObjectJsonException {
    final var value = node.get(field);
    if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
      throw invalid(field, "non-empty text", value);
    }
    return value.textValue();
  }

  long longValue(final String field) throws InvalidObjectJsonException {
    final var value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw invalid(field, "a 64-bit integer", value);
    }
    return value.longValue();
  }

  UUID uuid(final String field) throws InvalidObjectJsonException {
    final var value = node.get(field);
    if (value == null || !value.isTextual() || !UUID_TEXT.matcher(value.textValue()).matches()) {
      throw invalid(field, "a UUID", value);
    }
    return UUID.fromString(value.textValue());
  }

  Instant instant(final String field) throws InvalidObjectJsonException {
    final var text = text(field);
    try {
      return Instant.parse(text);
    } catch (final DateTimeParseException e) {
      throw new InvalidObjectJsonException(path(field), "an ISO-8601 instant", shown(text), e);
    }
  }

  /** The objects of an array field. */
  List<JsonFields> objects(final String field) throws InvalidObjectJsonException {
    final var value = node.get(field);
    if (value == null || !value.isArray()) {
      throw invalid(field, "an array", value);
    }
    final var objects = new ArrayList<JsonFields>();
    for (int i = 0; i < value.size(); i++) {
      final var element = value.get(i);
      final var elementPath = path(field) + "[" + i + "]";
      if (!element.isObject()) {
        throw new InvalidObjectJsonException(elementPath, "a JSON object", shown(element));
      }
      objects.add(new JsonFields(element, elementPath));
    }
    return objects;
  }

  /** The path of a field of this object, from the document's root. */
  String path(final String field) {
    return path.isEmpty() ? field : path + "." + field;
  }

  private InvalidObjectJsonException invalid(
      final String field, final String expected, @Nullable final JsonNode found) {
    return new InvalidObjectJsonException(path(field), expected, shown(found));
  }

  /** The text for a message: the JSON, cut off if it is long. */
  static String shown(@Nullable final Object json) {
    if (json == null) {
      return "nothing";
    }
    final var text =
        json instanceof final byte[] bytes
            ? new String(bytes, StandardCharsets.UTF_8)
            : json.toString();
    return text.length() <= MAX_SHOWN ? text : text.substring(0, MAX_SHOWN) + "…";
  }
}
