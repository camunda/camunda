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
import io.camunda.service.GroupServices.GroupAttribute;
import io.camunda.service.GroupServices.GroupDTO;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Translates a SCIM {@link PatchRequest} on a group into a masked attribute update plus an ordered
 * list of membership changes.
 *
 * <p>Supported paths (case-insensitive): {@code displayName}, {@code externalId} and {@code
 * members}. {@code add members} adds the given members, {@code replace members} replaces the whole
 * membership, {@code remove members[value eq "x"]} removes one member, and {@code remove members}
 * removes the listed members of its value, or all members if it has none. A path-less operation
 * takes an object whose keys are interpreted as paths; a key that is an extension schema URN holds
 * an object of attributes of that extension.
 *
 * <p>Configured attributes (see {@link ScimAttributeMapping}) are set by {@code add}/{@code
 * replace} and removed by {@code remove} on exactly their path; a path into one is rejected with
 * {@code invalidPath}. A configured path that is also a handled core attribute (e.g. {@code
 * displayName}) updates both the configured attribute and the core field. Any other path is
 * ignored, as IdPs send attributes that are not kept; a member that is a group is rejected with
 * {@code invalidValue}, an unsupported sub-path of a handled attribute with {@code invalidPath}.
 */
public class ScimGroupPatchTranslator {

  /** How a membership change is applied. */
  public enum MemberChangeType {
    ADD,
    REMOVE,
    /** Replaces the whole membership; an empty list removes every member. */
    SET
  }

  public record MemberChange(MemberChangeType type, List<String> memberIds) {}

  public record GroupPatch(
      GroupDTO group, Set<GroupAttribute> changedAttributes, List<MemberChange> memberChanges) {}

  private static final Logger LOG = LoggerFactory.getLogger(ScimGroupPatchTranslator.class);
  private static final Set<String> HANDLED_ATTRIBUTES =
      Set.of("displayname", "externalid", "members");

  private final ScimGroupMapper mapper;
  private final ScimAttributeMapping attributes;

  public ScimGroupPatchTranslator(final ScimGroupMapper mapper) {
    this.mapper = mapper;
    attributes = mapper.attributes();
  }

  public GroupPatch translate(final PatchRequest request, final String id) throws ScimException {
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
      if (!isHandledAttribute(path)) {
        return;
      }
      // a configured path can also be a handled core attribute (e.g. displayName): both the
      // custom attribute and the core field are updated, so core handling continues below
    } else if (!isHandledAttribute(path)) {
      LOG.debug("Ignoring patch path {} that is not a configured attribute", path);
      return;
    }
    if (path.size() != 1) {
      throw invalidPath(path);
    }
    final var attribute = path.getElement(0).getAttribute().toLowerCase(Locale.ROOT);
    final var valueFilter = path.getElement(0).getValueFilter();
    final boolean remove = type == PatchOpType.REMOVE;
    if (valueFilter != null && !("members".equals(attribute) && remove)) {
      throw invalidPath(path);
    }

    switch (attribute) {
      case "displayname" -> {
        if (remove) {
          throw BadRequestException.invalidValue("displayName is required and cannot be removed");
        }
        state.setDisplayName(asRequiredText(value, "displayName"));
      }
      case "externalid" -> state.setExternalId(remove ? "" : asText(value));
      case "members" -> applyMembers(path, type, value, valueFilter, state);
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
    final boolean core = ScimGroupMapper.GROUP_SCHEMA.equalsIgnoreCase(root);
    if (!core
        && (ScimGroupMapper.GROUP_SCHEMA.equalsIgnoreCase(path.getSchemaUrn())
            || attributes.match(path).kind() != MatchKind.NONE)) {
      return false;
    }
    for (final var field : value.properties()) {
      applyPath(
          Path.fromString(core ? field.getKey() : root + ":" + field.getKey()),
          type,
          field.getValue(),
          state);
    }
    return true;
  }

  private static boolean isHandledAttribute(final Path path) {
    final var urn = path.getSchemaUrn();
    return path.size() >= 1
        && (urn == null || ScimGroupMapper.GROUP_SCHEMA.equalsIgnoreCase(urn))
        && HANDLED_ATTRIBUTES.contains(path.getElement(0).getAttribute().toLowerCase(Locale.ROOT));
  }

  private void applyMembers(
      final Path path,
      final PatchOpType type,
      final JsonNode value,
      final Filter valueFilter,
      final PatchState state)
      throws ScimException {
    switch (type) {
      case ADD ->
          state.addMemberChange(new MemberChange(MemberChangeType.ADD, mapper.memberIds(value)));
      case REPLACE ->
          state.addMemberChange(new MemberChange(MemberChangeType.SET, mapper.memberIds(value)));
      case REMOVE -> {
        if (valueFilter != null) {
          state.addMemberChange(
              new MemberChange(
                  MemberChangeType.REMOVE, List.of(memberFromFilter(path, valueFilter))));
        } else if (value != null && !value.isNull()) {
          state.addMemberChange(new MemberChange(MemberChangeType.REMOVE, mapper.memberIds(value)));
        } else {
          state.addMemberChange(new MemberChange(MemberChangeType.SET, List.of()));
        }
      }
    }
  }

  private static String memberFromFilter(final Path path, final Filter filter)
      throws BadRequestException {
    final var filterPath = filter.getAttributePath();
    if (filter.getFilterType() == FilterType.EQUAL
        && filterPath != null
        && filterPath.size() == 1
        && "value".equals(filterPath.getElement(0).getAttribute().toLowerCase(Locale.ROOT))
        && filter.getComparisonValue().isString()) {
      return filter.getComparisonValue().asString();
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

  private static String asRequiredText(final JsonNode value, final String attribute)
      throws BadRequestException {
    final var text = asText(value);
    if (text.isBlank()) {
      throw BadRequestException.invalidValue(attribute + " requires a value");
    }
    return text;
  }

  private static BadRequestException invalidPath(final Path path) {
    return BadRequestException.invalidPath("Unsupported patch path: " + path);
  }

  private static final class PatchState {
    private final String id;
    private final Set<GroupAttribute> changed = EnumSet.noneOf(GroupAttribute.class);
    private final List<MemberChange> memberChanges = new ArrayList<>();
    private String displayName = "";
    private String externalId = "";
    private final Map<String, Object> attributeChanges = new HashMap<>();

    private PatchState(final String id) {
      this.id = id;
    }

    private void setDisplayName(final String value) {
      displayName = value;
      changed.add(GroupAttribute.NAME);
    }

    private void setExternalId(final String value) {
      externalId = value;
      changed.add(GroupAttribute.EXTERNAL_ID);
    }

    private void addMemberChange(final MemberChange change) {
      if (change.type() == MemberChangeType.SET) {
        // a replacement makes everything before it irrelevant
        memberChanges.clear();
      }
      memberChanges.add(change);
    }

    private void setAttribute(final String name, final @Nullable Object value) {
      attributeChanges.put(name, value);
      changed.add(GroupAttribute.ATTRIBUTES_MERGE);
    }

    private GroupPatch toPatch() throws BadRequestException {
      ScimAttributeMapping.checkSize(attributeChanges);
      return new GroupPatch(
          new GroupDTO(id, displayName, "", externalId, attributeChanges),
          changed,
          List.copyOf(memberChanges));
    }
  }
}
