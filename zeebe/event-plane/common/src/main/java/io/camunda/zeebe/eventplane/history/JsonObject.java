/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A JSON object that is read field by field, where each field that is missing or of another type is
 * an {@link InvalidHistoryJsonException} that names it. Fields that aren't asked for are skipped.
 */
final class JsonObject {

  static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Pattern UUID_TEXT =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final int MAX_SHOWN = 200;

  private final JsonNode node;
  private final String path;

  private JsonObject(final JsonNode node, final String path) {
    this.node = node;
    this.path = path;
  }

  static JsonObject parse(final byte[] json) throws IOException {
    final var root = MAPPER.readTree(json);
    if (root == null || !root.isObject()) {
      throw new InvalidHistoryJsonException("the document", "a JSON object", shown(root));
    }
    return new JsonObject(root, "");
  }

  /** The text for a message: the JSON, cut off if it is long. */
  static String shown(@Nullable final Object json) {
    if (json == null || json instanceof final JsonNode node && node.isMissingNode()) {
      return "nothing";
    }
    final var text =
        json instanceof final byte[] bytes
            ? new String(bytes, StandardCharsets.UTF_8)
            : json.toString();
    return text.length() <= MAX_SHOWN ? text : text.substring(0, MAX_SHOWN) + "…";
  }

  List<String> fieldNames() {
    final var names = new ArrayList<String>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  JsonObject object(final String field) throws InvalidHistoryJsonException {
    final var value = node.get(field);
    if (value == null || !value.isObject()) {
      throw invalid(field, "a JSON object", value);
    }
    return new JsonObject(value, path(field));
  }

  String text(final String field) throws InvalidHistoryJsonException {
    final var value = node.get(field);
    if (value == null || !value.isTextual()) {
      throw invalid(field, "text", value);
    }
    return value.textValue();
  }

  UUID uuid(final String field) throws InvalidHistoryJsonException {
    final var value = node.get(field);
    return parseUuid(field, value);
  }

  /** A UUID, or null if the field is null or missing. */
  @Nullable UUID optionalUuid(final String field) throws InvalidHistoryJsonException {
    final var value = node.get(field);
    return value == null || value.isNull() ? null : parseUuid(field, value);
  }

  long longValue(final String field) throws InvalidHistoryJsonException {
    final var value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw invalid(field, "a 64-bit integer", value);
    }
    return value.longValue();
  }

  int intValue(final String field) throws InvalidHistoryJsonException {
    final var value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
      throw invalid(field, "a 32-bit integer", value);
    }
    return value.intValue();
  }

  Instant instant(final String field) throws InvalidHistoryJsonException {
    final var text = text(field);
    try {
      return Instant.parse(text);
    } catch (final DateTimeParseException e) {
      throw new InvalidHistoryJsonException(path(field), "an ISO-8601 instant", shown(text), e);
    }
  }

  /** The path of a field of this object, from the document's root. */
  String path(final String field) {
    return path.isEmpty() ? field : path + "." + field;
  }

  private UUID parseUuid(final String field, @Nullable final JsonNode value)
      throws InvalidHistoryJsonException {
    if (value == null || !value.isTextual() || !UUID_TEXT.matcher(value.textValue()).matches()) {
      throw invalid(field, "a UUID", value);
    }
    return UUID.fromString(value.textValue());
  }

  private InvalidHistoryJsonException invalid(
      final String field, final String expected, @Nullable final JsonNode found) {
    return new InvalidHistoryJsonException(path(field), expected, shown(found));
  }
}
