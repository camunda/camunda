/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import com.unboundid.scim2.common.Path;
import com.unboundid.scim2.common.exceptions.BadRequestException;
import io.camunda.gateway.scim.ScimAttributeProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The validated attribute configuration of one resource type, and the translation between SCIM
 * resources and the stored {@code name -> value} map. Values keep their SCIM JSON shape.
 */
public final class ScimAttributeMapping {

  public static final int MAX_SERIALIZED_BYTES = 16 * 1024;

  private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  /** How a patch path relates to the configured attributes. */
  public enum MatchKind {
    NONE,
    /** The path is a configured attribute. */
    EXACT,
    /** The path points into a configured attribute, which is not supported. */
    INSIDE
  }

  public record Match(MatchKind kind, @Nullable Attribute attribute) {}

  /** A configured attribute; the elements are lower-cased attribute names of its path. */
  public record Attribute(
      String name, String path, @Nullable String urn, List<String> elements, List<String> names) {}

  private static final Match NO_MATCH = new Match(MatchKind.NONE, null);

  private final String coreUrn;
  private final List<Attribute> attributes;

  private ScimAttributeMapping(final String coreUrn, final List<Attribute> attributes) {
    this.coreUrn = coreUrn;
    this.attributes = attributes;
  }

  public static ScimAttributeMapping empty(final String coreUrn) {
    return new ScimAttributeMapping(coreUrn, List.of());
  }

  /**
   * @param property the configuration key, used in error messages
   * @throws IllegalArgumentException if the configuration is invalid
   */
  public static ScimAttributeMapping of(
      final String property,
      final @Nullable List<ScimAttributeProperties.Attribute> configured,
      final String coreUrn) {
    if (configured == null || configured.isEmpty()) {
      return empty(coreUrn);
    }
    final var result = new ArrayList<Attribute>();
    final Set<String> names = new HashSet<>();
    final Set<String> keys = new HashSet<>();
    for (int i = 0; i < configured.size(); i++) {
      final var where = property + "[" + i + "]";
      final var entry = configured.get(i);
      if (entry == null || entry.getName() == null || !NAME.matcher(entry.getName()).matches()) {
        throw new IllegalArgumentException(
            where
                + ".name must match "
                + NAME.pattern()
                + " but was: "
                + (entry == null ? null : entry.getName()));
      }
      if (entry.getPath() == null || entry.getPath().isBlank()) {
        throw new IllegalArgumentException(where + ".path is required");
      }
      final Path path;
      try {
        path = Path.fromString(entry.getPath());
      } catch (final BadRequestException | RuntimeException e) {
        throw new IllegalArgumentException(
            where + ".path '" + entry.getPath() + "' is not a valid SCIM attribute path", e);
      }
      if (path.size() < 1) {
        throw new IllegalArgumentException(where + ".path '" + entry.getPath() + "' is empty");
      }
      final var elements = new ArrayList<String>();
      final var originalNames = new ArrayList<String>();
      for (final var element : path) {
        if (element.getValueFilter() != null) {
          throw new IllegalArgumentException(
              where + ".path '" + entry.getPath() + "' must not contain a value filter");
        }
        elements.add(element.getAttribute().toLowerCase(Locale.ROOT));
        originalNames.add(element.getAttribute());
      }
      final var urn = normalizeUrn(path.getSchemaUrn(), coreUrn);
      if (!names.add(entry.getName())) {
        throw new IllegalArgumentException(
            where + ".name '" + entry.getName() + "' is not unique in " + property);
      }
      if (!keys.add(key(urn, elements))) {
        throw new IllegalArgumentException(
            where + ".path '" + entry.getPath() + "' is not unique in " + property);
      }
      result.add(new Attribute(entry.getName(), entry.getPath(), urn, elements, originalNames));
    }
    return new ScimAttributeMapping(coreUrn, List.copyOf(result));
  }

  public boolean isEmpty() {
    return attributes.isEmpty();
  }

  public List<Attribute> attributes() {
    return attributes;
  }

  /** Classifies a patch path against the configured attributes. */
  public Match match(final Path path) {
    if (attributes.isEmpty() || path.size() < 1) {
      return NO_MATCH;
    }
    final var urn = normalizeUrn(path.getSchemaUrn(), coreUrn);
    final var elements = new ArrayList<String>();
    boolean filtered = false;
    for (final var element : path) {
      elements.add(element.getAttribute().toLowerCase(Locale.ROOT));
      filtered |= element.getValueFilter() != null;
    }
    for (final var attribute : attributes) {
      if (!sameUrn(attribute.urn(), urn)) {
        continue;
      }
      if (elements.equals(attribute.elements())) {
        return new Match(filtered ? MatchKind.INSIDE : MatchKind.EXACT, attribute);
      }
      if (elements.size() > attribute.elements().size()
          && elements.subList(0, attribute.elements().size()).equals(attribute.elements())) {
        return new Match(MatchKind.INSIDE, attribute);
      }
    }
    return NO_MATCH;
  }

  /** Reads every configured attribute that is present in the resource. */
  public Map<String, Object> extract(final ObjectNode resource) throws BadRequestException {
    final Map<String, Object> result = new LinkedHashMap<>();
    for (final var attribute : attributes) {
      final var value = lookup(resource, attribute);
      if (value != null && !value.isNull() && !value.isMissingNode()) {
        result.put(attribute.name(), toJava(value));
      }
    }
    checkSize(result);
    return result;
  }

  /** Writes the stored attributes at their configured SCIM paths. Null values are skipped. */
  public ObjectNode writeTo(final ObjectNode resource, final @Nullable Map<String, Object> stored) {
    if (stored == null || stored.isEmpty()) {
      return resource;
    }
    for (final var attribute : attributes) {
      final var value = stored.get(attribute.name());
      if (value == null) {
        continue;
      }
      ObjectNode target = resource;
      if (attribute.urn() != null) {
        target = objectProperty(resource, attribute.urn());
        addSchema(resource, attribute.urn());
      }
      final var names = attribute.names();
      for (int i = 0; i < names.size() - 1; i++) {
        target = objectProperty(target, names.get(i));
      }
      target.set(names.getLast(), MAPPER.valueToTree(value));
    }
    return resource;
  }

  /**
   * @throws BadRequestException if the serialized attributes exceed {@value MAX_SERIALIZED_BYTES}
   */
  public static void checkSize(final Map<String, Object> values) throws BadRequestException {
    try {
      if (MAPPER.writeValueAsBytes(values).length > MAX_SERIALIZED_BYTES) {
        throw BadRequestException.invalidValue(
            "The configured attributes exceed the limit of " + MAX_SERIALIZED_BYTES + " bytes");
      }
    } catch (final JacksonException e) {
      throw BadRequestException.invalidValue("The attributes cannot be serialized");
    }
  }

  /** Converts a JSON value to plain Java: Map, List, String, Number, Boolean or null. */
  public static @Nullable Object toJava(final @Nullable JsonNode value) {
    if (value == null || value.isNull() || value.isMissingNode()) {
      return null;
    }
    return MAPPER.convertValue(value, Object.class);
  }

  private @Nullable JsonNode lookup(final ObjectNode resource, final Attribute attribute) {
    JsonNode current = resource;
    if (attribute.urn() != null) {
      current = field(current, attribute.urn());
    }
    for (final var element : attribute.elements()) {
      if (current == null || current.isNull()) {
        return null;
      }
      if (current.isArray()) {
        final ArrayNode collected = MAPPER.createArrayNode();
        for (final var item : current) {
          final var sub = field(item, element);
          if (sub != null && !sub.isNull()) {
            collected.add(sub);
          }
        }
        current = collected.isEmpty() ? null : collected;
      } else {
        current = field(current, element);
      }
    }
    return current;
  }

  private static @Nullable JsonNode field(final JsonNode node, final String name) {
    if (!node.isObject()) {
      return null;
    }
    for (final var entry : node.properties()) {
      if (entry.getKey().equalsIgnoreCase(name)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static ObjectNode objectProperty(final ObjectNode parent, final String name) {
    final var existing = parent.get(name);
    if (existing instanceof final ObjectNode object) {
      return object;
    }
    return parent.putObject(name);
  }

  private static void addSchema(final ObjectNode resource, final String urn) {
    final var schemas = resource.get("schemas");
    if (schemas instanceof final ArrayNode array) {
      for (final var schema : array) {
        if (schema.isString() && schema.asString().equalsIgnoreCase(urn)) {
          return;
        }
      }
      array.add(urn);
    } else {
      resource.putArray("schemas").add(urn);
    }
  }

  private static boolean sameUrn(final @Nullable String a, final @Nullable String b) {
    return a == null ? b == null : a.equalsIgnoreCase(b);
  }

  private static @Nullable String normalizeUrn(final @Nullable String urn, final String coreUrn) {
    if (urn == null || urn.equalsIgnoreCase(coreUrn)) {
      return null;
    }
    return urn;
  }

  private static String key(final @Nullable String urn, final List<String> elements) {
    return (urn == null ? "" : urn.toLowerCase(Locale.ROOT)) + "|" + String.join(".", elements);
  }
}
