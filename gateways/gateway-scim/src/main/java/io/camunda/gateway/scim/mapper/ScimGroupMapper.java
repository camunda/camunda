/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import com.unboundid.scim2.common.types.Meta;
import com.unboundid.scim2.common.utils.JsonUtils;
import io.camunda.search.entities.GroupEntity;
import io.camunda.service.GroupServices.GroupAttribute;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Maps between the SCIM {@link GroupResource} and the Camunda group representations. The SCIM
 * {@code id} is the Camunda group id, {@code displayName} the Camunda name. The Camunda description
 * is not part of SCIM and is therefore never written by SCIM requests. Members are users only; a
 * member's {@code value} is the username, which is also the SCIM id of the user.
 */
public class ScimGroupMapper {

  public static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
  private static final String GROUP_MEMBER_TYPE = "Group";

  /** The attributes SCIM may write; the description is deliberately left out. */
  public static final Set<GroupAttribute> REPLACED_ATTRIBUTES =
      EnumSet.of(GroupAttribute.NAME, GroupAttribute.EXTERNAL_ID);

  private final ScimAttributeMapping attributes;

  public ScimGroupMapper() {
    this(ScimAttributeMapping.empty(GROUP_SCHEMA));
  }

  public ScimGroupMapper(final ScimAttributeMapping attributes) {
    this.attributes = attributes;
  }

  public ScimAttributeMapping attributes() {
    return attributes;
  }

  /** The attributes a PUT overwrites. */
  public Set<GroupAttribute> replacedAttributes() {
    final Set<GroupAttribute> replaced = EnumSet.copyOf(REPLACED_ATTRIBUTES);
    if (!attributes.isEmpty()) {
      replaced.add(GroupAttribute.ATTRIBUTES);
    }
    return replaced;
  }

  public GroupDTO toCreateDto(final GroupResource resource, final String groupId)
      throws BadRequestException {
    return toCreateDto(resource, groupId, null);
  }

  /**
   * @param raw the request body, from which the configured attributes are read
   */
  public GroupDTO toCreateDto(
      final GroupResource resource, final String groupId, final @Nullable ObjectNode raw)
      throws BadRequestException {
    return toDto(groupId, resource, raw);
  }

  public GroupDTO toReplaceDto(final GroupResource resource, final String id)
      throws BadRequestException {
    return toReplaceDto(resource, id, null);
  }

  public GroupDTO toReplaceDto(
      final GroupResource resource, final String id, final @Nullable ObjectNode raw)
      throws BadRequestException {
    return toDto(id, resource, raw);
  }

  /** The SCIM JSON of a write response, with the stored attributes at their configured paths. */
  public ObjectNode toJson(
      final GroupRecord record,
      final @Nullable List<String> members,
      final URI location,
      final Function<String, URI> memberRef) {
    return attributes.writeTo(
        JsonUtils.valueToNode(toResource(record, members, location, memberRef)),
        record.getAttributes());
  }

  /** The SCIM JSON of a read response, with the stored attributes at their configured paths. */
  public ObjectNode toJson(
      final GroupEntity entity,
      final @Nullable List<String> members,
      final URI location,
      final Function<String, URI> memberRef) {
    return attributes.writeTo(
        JsonUtils.valueToNode(toResource(entity, members, location, memberRef)),
        entity.attributes());
  }

  /** The usernames of the members; fails for a member that is not a user. */
  public List<String> memberIds(final @Nullable List<Member> members) throws BadRequestException {
    if (members == null) {
      return List.of();
    }
    final Set<String> ids = new LinkedHashSet<>();
    for (final Member member : members) {
      ids.add(memberId(member.getValue(), member.getType()));
    }
    return List.copyOf(ids);
  }

  /** Parses a {@code members} patch value: an array of member objects, or a single one. */
  public List<String> memberIds(final @Nullable JsonNode value) throws BadRequestException {
    if (value == null || value.isNull()) {
      return List.of();
    }
    final Set<String> ids = new LinkedHashSet<>();
    if (value.isArray()) {
      for (final JsonNode element : value) {
        ids.add(memberId(element));
      }
    } else {
      ids.add(memberId(value));
    }
    return List.copyOf(ids);
  }

  public GroupResource toResource(
      final GroupRecord record,
      final @Nullable List<String> members,
      final URI location,
      final Function<String, URI> memberRef) {
    return toResource(
        record.getGroupId(),
        record.getName(),
        record.getExternalId(),
        members,
        location,
        memberRef);
  }

  public GroupResource toResource(
      final GroupEntity entity,
      final @Nullable List<String> members,
      final URI location,
      final Function<String, URI> memberRef) {
    final var resource =
        toResource(
            entity.groupId(), entity.name(), entity.externalId(), members, location, memberRef);
    setTimestamps(resource.getMeta(), entity.createdAt(), entity.lastModifiedAt());
    return resource;
  }

  private static void setTimestamps(
      final Meta meta,
      final @Nullable OffsetDateTime createdAt,
      final @Nullable OffsetDateTime lastModifiedAt) {
    if (createdAt != null) {
      meta.setCreatedMillis(createdAt.toInstant().toEpochMilli());
    }
    if (lastModifiedAt != null) {
      meta.setLastModifiedMillis(lastModifiedAt.toInstant().toEpochMilli());
    }
  }

  private GroupDTO toDto(
      final String groupId, final GroupResource resource, final @Nullable ObjectNode raw)
      throws BadRequestException {
    final var displayName = resource.getDisplayName();
    if (displayName == null || displayName.isBlank()) {
      throw BadRequestException.invalidValue("displayName is required");
    }
    final Map<String, Object> extracted = raw == null ? Map.of() : attributes.extract(raw);
    return new GroupDTO(
        groupId,
        displayName,
        "",
        resource.getExternalId() == null ? "" : resource.getExternalId(),
        extracted);
  }

  private static String memberId(final JsonNode member) throws BadRequestException {
    if (!member.isObject()) {
      throw BadRequestException.invalidValue("A member must be an object but got: " + member);
    }
    final var value = member.get("value");
    final var type = member.get("type");
    return memberId(
        value != null && value.isString() ? value.asString() : null,
        type != null && type.isString() ? type.asString() : null);
  }

  private static String memberId(final @Nullable String value, final @Nullable String type)
      throws BadRequestException {
    if (GROUP_MEMBER_TYPE.equalsIgnoreCase(type)) {
      throw BadRequestException.invalidValue("Groups cannot be members of a group");
    }
    if (value == null || value.isBlank()) {
      throw BadRequestException.invalidValue("A member requires a value");
    }
    return value;
  }

  private static GroupResource toResource(
      final String groupId,
      final String name,
      final @Nullable String externalId,
      final @Nullable List<String> members,
      final URI location,
      final Function<String, URI> memberRef) {
    final var resource = new GroupResource();
    resource.setId(groupId);
    resource.setDisplayName(name);
    resource.setSchemaUrns(GROUP_SCHEMA);
    resource.setMeta(new Meta().setResourceType("Group").setLocation(location));
    if (externalId != null && !externalId.isBlank()) {
      resource.setExternalId(externalId);
    }
    if (members != null) {
      final List<Member> scimMembers = new ArrayList<>();
      for (final String username : members) {
        scimMembers.add(new Member().setValue(username).setRef(memberRef.apply(username)));
      }
      resource.setMembers(scimMembers);
    }
    return resource;
  }
}
