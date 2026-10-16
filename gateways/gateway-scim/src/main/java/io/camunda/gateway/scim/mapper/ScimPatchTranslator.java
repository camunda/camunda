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
import com.unboundid.scim2.common.exceptions.ScimException;
import com.unboundid.scim2.common.filters.Filter;
import com.unboundid.scim2.common.filters.FilterType;
import com.unboundid.scim2.common.messages.PatchOpType;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.messages.PatchRequest;
import io.camunda.gateway.scim.mapper.ScimAttributeMapping.MatchKind;
import io.camunda.service.UserServices.UserAttribute;
import io.camunda.service.UserServices.UserDTO;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Translates a SCIM {@link PatchRequest} into the attributes to change. The result is applied by
 * the engine as a masked update, so nothing is read-modified-written here.
 *
 * <p>Supported paths (case-insensitive): {@code active}, {@code externalId}, {@code displayName},
 * {@code name.formatted|givenName|familyName}, {@code emails}, {@code emails[type eq "work"]},
 * {@code emails[primary eq true]} (each optionally followed by {@code .value}) and {@code userName}
 * as long as it equals the current one. A path-less operation takes an object whose keys are
 * interpreted as paths; a key that is an extension schema URN holds an object of attributes of that
 * extension. {@code remove} is supported for {@code externalId}, {@code displayName}, {@code
 * name.*} and {@code emails}.
 *
 * <p>Configured attributes (see {@link ScimAttributeMapping}) are set by {@code add}/{@code
 * replace} and removed by {@code remove} on exactly their path; a path into one is rejected with
 * {@code invalidPath}. A configured path that is also a handled core attribute (e.g. {@code
 * displayName}) updates both the configured attribute and the core field. Paths that are neither
 * configured nor handled core attributes are ignored, as IdPs send attributes that are not kept. An
 * unsupported sub-path of a handled core attribute is rejected with {@code invalidPath}.
 */
public class ScimPatchTranslator {

  private static final Logger LOG = LoggerFactory.getLogger(ScimPatchTranslator.class);
  private static final Set<String> HANDLED_ATTRIBUTES =
      Set.of("active", "externalid", "displayname", "name", "emails", "username");

  private final ScimAttributeMapping attributes;

  public ScimPatchTranslator() {
    this(ScimAttributeMapping.empty(ScimUserMapper.USER_SCHEMA));
  }

  public ScimPatchTranslator(final ScimAttributeMapping attributes) {
    this.attributes = attributes;
  }

  public record UserPatch(UserDTO user, Set<UserAttribute> changedAttributes) {}

  public UserPatch translate(final PatchRequest request, final String id) throws ScimException {
    final var state = new PatchState(id);
    for (final PatchOperation operation : request.getOperations()) {
      applyOperation(operation, state);
    }
    return state.toPatch();
  }

  private void applyOperation(final PatchOperation operation, final PatchState state)
      throws ScimException {
    final var type = operation.getOpType();
    final var path = operation.getPath();
    final JsonNode value = operation.getJsonNode();
    if (path != null) {
      applyPath(path, type, value, state);
      return;
    }
    if (type == PatchOpType.REMOVE) {
      throw BadRequestException.noTarget("A remove operation requires a path");
    }
    if (value == null || !value.isObject()) {
      throw BadRequestException.invalidValue("A path-less operation requires an object value");
    }
    for (final var field : value.properties()) {
      applyPath(Path.fromString(field.getKey()), type, field.getValue(), state);
    }
  }

  private void applyPath(
      final Path path, final PatchOpType type, final JsonNode value, final PatchState state)
      throws ScimException {
    if (expandSchemaRoot(path, type, value, state)) {
      return;
    }
    final var match = attributes.match(path);
    if (match.kind() == MatchKind.INSIDE) {
      throw invalidPath(path);
    }
    if (match.kind() == MatchKind.EXACT) {
      final boolean removed = type == PatchOpType.REMOVE || value == null || value.isNull();
      state.setAttribute(
          match.attribute().name(), removed ? null : ScimAttributeMapping.toJava(value));
      if (!isHandledCoreAttribute(path)) {
        return;
      }
      // a configured path can also be a handled core attribute (e.g. displayName): both the
      // custom attribute and the core field are updated, so core handling continues below
    } else if (!isHandledCoreAttribute(path)) {
      LOG.debug("Ignoring patch path {} that is not a configured attribute", path);
      return;
    }
    if (path.size() < 1 || path.size() > 2) {
      throw invalidPath(path);
    }
    final var attribute = path.getElement(0).getAttribute().toLowerCase(Locale.ROOT);
    final var subAttribute =
        path.size() == 2 ? path.getElement(1).getAttribute().toLowerCase(Locale.ROOT) : null;
    final var valueFilter = path.getElement(0).getValueFilter();
    if (path.size() == 2 && path.getElement(1).getValueFilter() != null
        || valueFilter != null && !"emails".equals(attribute)) {
      throw invalidPath(path);
    }
    final boolean remove = type == PatchOpType.REMOVE;

    switch (attribute) {
      case "active" -> {
        requireSimple(path, subAttribute);
        if (remove) {
          throw invalidPath(path);
        }
        state.setActive(asBoolean(value));
      }
      case "externalid" -> {
        requireSimple(path, subAttribute);
        state.setExternalId(remove ? "" : asText(value));
      }
      case "displayname" -> {
        requireSimple(path, subAttribute);
        state.setDisplayName(remove ? "" : asText(value));
      }
      case "name" -> applyName(path, subAttribute, remove, value, state);
      case "emails" -> {
        if (subAttribute != null && !"value".equals(subAttribute)) {
          throw invalidPath(path);
        }
        if (valueFilter != null) {
          requireWorkOrPrimaryFilter(path, valueFilter);
        }
        state.setEmail(remove ? "" : asEmail(value));
      }
      case "username" -> {
        requireSimple(path, subAttribute);
        if (remove || !state.id.equals(asText(value))) {
          throw BadRequestException.mutability("userName cannot be changed");
        }
      }
      default -> throw invalidPath(path);
    }
  }

  /**
   * A path that is a schema URN itself carries an object of that schema's attributes, as sent by
   * Entra in a path-less operation. Returns whether the path was such a root and has been applied.
   */
  private boolean expandSchemaRoot(
      final Path path, final PatchOpType type, final JsonNode value, final PatchState state)
      throws ScimException {
    if (value == null
        || !value.isObject()
        || path.size() != 1
        || path.getSchemaUrn() == null
        || path.getElement(0).getValueFilter() != null) {
      return false;
    }
    final var root = path.toString();
    if (!ScimUserMapper.USER_SCHEMA.equalsIgnoreCase(root)
        && (ScimUserMapper.USER_SCHEMA.equalsIgnoreCase(path.getSchemaUrn())
            || attributes.match(path).kind() != MatchKind.NONE)) {
      return false;
    }
    final boolean core = ScimUserMapper.USER_SCHEMA.equalsIgnoreCase(root);
    for (final var field : value.properties()) {
      applyPath(
          Path.fromString(core ? field.getKey() : root + ":" + field.getKey()),
          type,
          field.getValue(),
          state);
    }
    return true;
  }

  private static boolean isHandledCoreAttribute(final Path path) {
    final var urn = path.getSchemaUrn();
    return path.size() >= 1
        && (urn == null || ScimUserMapper.USER_SCHEMA.equalsIgnoreCase(urn))
        && HANDLED_ATTRIBUTES.contains(path.getElement(0).getAttribute().toLowerCase(Locale.ROOT));
  }

  private void applyName(
      final Path path,
      final String subAttribute,
      final boolean remove,
      final JsonNode value,
      final PatchState state)
      throws ScimException {
    if (subAttribute == null) {
      if (remove) {
        state.setNameParts("", "", "");
      } else if (value != null && value.isObject()) {
        for (final var field : value.properties()) {
          applyNamePart(path, field.getKey().toLowerCase(Locale.ROOT), field.getValue(), state);
        }
      } else {
        throw BadRequestException.invalidValue("name requires an object value");
      }
      return;
    }
    applyNamePart(path, subAttribute, remove ? null : value, state);
  }

  private void applyNamePart(
      final Path path, final String part, final JsonNode value, final PatchState state)
      throws ScimException {
    final var text = value == null ? "" : asText(value);
    switch (part) {
      case "formatted" -> state.setFormatted(text);
      case "givenname" -> state.setGivenName(text);
      case "familyname" -> state.setFamilyName(text);
      default -> throw invalidPath(path);
    }
  }

  private static void requireSimple(final Path path, final String subAttribute)
      throws BadRequestException {
    if (subAttribute != null || path.getElement(0).getValueFilter() != null) {
      throw invalidPath(path);
    }
  }

  private static void requireWorkOrPrimaryFilter(final Path path, final Filter filter)
      throws BadRequestException {
    final var filterPath = filter.getAttributePath();
    if (filter.getFilterType() == FilterType.EQUAL
        && filterPath != null
        && filterPath.size() == 1) {
      final var comparison = filter.getComparisonValue();
      final var attribute = filterPath.getElement(0).getAttribute().toLowerCase(Locale.ROOT);
      if ("type".equals(attribute)
          && comparison.isString()
          && ScimUserMapper.WORK_EMAIL_TYPE.equalsIgnoreCase(comparison.asString())) {
        return;
      }
      if ("primary".equals(attribute) && comparison.isBoolean() && comparison.booleanValue()) {
        return;
      }
    }
    throw invalidPath(path);
  }

  private static String asText(final JsonNode value) throws BadRequestException {
    if (value == null || value.isNull()) {
      return "";
    }
    if (value.isString()) {
      return value.asString();
    }
    throw BadRequestException.invalidValue("Expected a string value but got: " + value);
  }

  /** Entra sends booleans as strings, e.g. {@code "False"}. */
  private static boolean asBoolean(final JsonNode value) throws BadRequestException {
    if (value != null && value.isBoolean()) {
      return value.booleanValue();
    }
    if (value != null && value.isString()) {
      final var text = value.asString();
      if ("true".equalsIgnoreCase(text)) {
        return true;
      }
      if ("false".equalsIgnoreCase(text)) {
        return false;
      }
    }
    throw BadRequestException.invalidValue("Expected a boolean value but got: " + value);
  }

  /**
   * Accepts a plain string, an email object, or an array of email objects (primary, else first).
   */
  private static String asEmail(final JsonNode value) throws BadRequestException {
    if (value == null || value.isNull()) {
      return "";
    }
    if (value.isString()) {
      return value.asString();
    }
    if (value.isObject()) {
      return asText(value.get("value"));
    }
    if (value.isArray()) {
      if (value.isEmpty()) {
        return "";
      }
      JsonNode selected = value.get(0);
      for (final JsonNode candidate : value) {
        if (candidate.isObject() && candidate.path("primary").asBoolean(false)) {
          selected = candidate;
          break;
        }
      }
      return asEmail(selected);
    }
    throw BadRequestException.invalidValue("Unsupported email value: " + value);
  }

  private static BadRequestException invalidPath(final Path path) {
    return BadRequestException.invalidPath("Unsupported patch path: " + path);
  }

  private static final class PatchState {
    private final String id;
    private final Set<UserAttribute> changed = EnumSet.noneOf(UserAttribute.class);
    private String displayName;
    private String formatted;
    private String givenName;
    private String familyName;
    private String email = "";
    private String externalId = "";
    private boolean active = true;
    private final Map<String, Object> attributeChanges = new HashMap<>();

    private PatchState(final String id) {
      this.id = id;
    }

    void setDisplayName(final String value) {
      displayName = value;
      changed.add(UserAttribute.NAME);
    }

    void setFormatted(final String value) {
      formatted = value;
      changed.add(UserAttribute.NAME);
    }

    void setGivenName(final String value) {
      givenName = value;
      changed.add(UserAttribute.NAME);
    }

    void setFamilyName(final String value) {
      familyName = value;
      changed.add(UserAttribute.NAME);
    }

    void setNameParts(final String formatted, final String givenName, final String familyName) {
      setFormatted(formatted);
      setGivenName(givenName);
      setFamilyName(familyName);
    }

    void setEmail(final String value) {
      email = value;
      changed.add(UserAttribute.EMAIL);
    }

    void setExternalId(final String value) {
      externalId = value;
      changed.add(UserAttribute.EXTERNAL_ID);
    }

    void setActive(final boolean value) {
      active = value;
      changed.add(UserAttribute.ACTIVE);
    }

    void setAttribute(final String name, final @Nullable Object value) {
      attributeChanges.put(name, value);
      changed.add(UserAttribute.ATTRIBUTES_MERGE);
    }

    UserPatch toPatch() throws BadRequestException {
      ScimAttributeMapping.checkSize(attributeChanges);
      final var name =
          displayName != null && !displayName.isBlank()
              ? displayName
              : ScimUserMapper.composeName(formatted, givenName, familyName);
      return new UserPatch(
          new UserDTO(id, name, email, "", externalId, active, attributeChanges), changed);
    }
  }
}
