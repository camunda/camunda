/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.Meta;
import com.unboundid.scim2.common.types.Name;
import com.unboundid.scim2.common.types.UserResource;
import com.unboundid.scim2.common.utils.JsonUtils;
import io.camunda.search.entities.UserEntity;
import io.camunda.service.UserServices.UserAttribute;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Maps between the SCIM {@link UserResource} and the Camunda user representations. The SCIM {@code
 * id} and {@code userName} are both the Camunda username.
 */
public class ScimUserMapper {

  public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
  public static final String WORK_EMAIL_TYPE = "work";

  private final ScimAttributeMapping attributes;

  public ScimUserMapper() {
    this(ScimAttributeMapping.empty(USER_SCHEMA));
  }

  public ScimUserMapper(final ScimAttributeMapping attributes) {
    this.attributes = attributes;
  }

  public ScimAttributeMapping attributes() {
    return attributes;
  }

  public UserDTO toCreateDto(final UserResource resource) throws BadRequestException {
    return toCreateDto(resource, null);
  }

  /**
   * @param raw the request body, from which the configured attributes are read
   */
  public UserDTO toCreateDto(final UserResource resource, final @Nullable ObjectNode raw)
      throws BadRequestException {
    final var userName = resource.getUserName();
    if (userName == null || userName.isBlank()) {
      throw BadRequestException.invalidValue("userName is required");
    }
    return toDto(userName, resource, raw);
  }

  /**
   * Maps a full replacement. The username is immutable, so a different {@code userName} is
   * rejected.
   */
  public UserDTO toReplaceDto(final UserResource resource, final String id)
      throws BadRequestException {
    return toReplaceDto(resource, id, null);
  }

  public UserDTO toReplaceDto(
      final UserResource resource, final String id, final @Nullable ObjectNode raw)
      throws BadRequestException {
    final var userName = resource.getUserName();
    if (userName != null && !userName.equals(id)) {
      throw BadRequestException.mutability("userName cannot be changed");
    }
    return toDto(id, resource, raw);
  }

  /** All attributes a PUT overwrites; the password only if the client sent one. */
  public Set<UserAttribute> replacedAttributes(final UserResource resource) {
    final Set<UserAttribute> replaced =
        EnumSet.of(
            UserAttribute.NAME,
            UserAttribute.EMAIL,
            UserAttribute.EXTERNAL_ID,
            UserAttribute.ACTIVE);
    if (resource.getPassword() != null && !resource.getPassword().isEmpty()) {
      replaced.add(UserAttribute.PASSWORD);
    }
    if (!attributes.isEmpty()) {
      replaced.add(UserAttribute.ATTRIBUTES);
    }
    return replaced;
  }

  public UserResource toResource(final UserRecord record, final URI location) {
    return toResource(
        record.getUsername(),
        record.getName(),
        record.getEmail(),
        record.getExternalId(),
        record.isActive(),
        location);
  }

  /** The SCIM JSON of a write response, with the stored attributes at their configured paths. */
  public ObjectNode toJson(final UserRecord record, final URI location) {
    return attributes.writeTo(
        JsonUtils.valueToNode(toResource(record, location)), record.getAttributes());
  }

  /** The SCIM JSON of a read response, with the stored attributes at their configured paths. */
  public ObjectNode toJson(final UserEntity entity, final URI location) {
    return attributes.writeTo(
        JsonUtils.valueToNode(toResource(entity, location)), entity.attributes());
  }

  public UserResource toResource(final UserEntity entity, final URI location) {
    final var resource =
        toResource(
            entity.username(),
            entity.name(),
            entity.email(),
            entity.externalId(),
            entity.active() == null || entity.active(),
            location);
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

  private UserDTO toDto(
      final String username, final UserResource resource, final @Nullable ObjectNode raw)
      throws BadRequestException {
    final Map<String, Object> extracted = raw == null ? Map.of() : attributes.extract(raw);
    return new UserDTO(
        username,
        resolveName(resource),
        resolveEmail(resource.getEmails()),
        resource.getPassword() == null ? "" : resource.getPassword(),
        resource.getExternalId() == null ? "" : resource.getExternalId(),
        resource.getActive() == null || resource.getActive(),
        extracted);
  }

  /** The Camunda name is the display name, else the formatted name, else "given family". */
  private static String resolveName(final UserResource resource) {
    if (isNotBlank(resource.getDisplayName())) {
      return resource.getDisplayName();
    }
    final Name name = resource.getName();
    if (name == null) {
      return "";
    }
    return composeName(name.getFormatted(), name.getGivenName(), name.getFamilyName());
  }

  public static String composeName(
      final String formatted, final String givenName, final String familyName) {
    if (isNotBlank(formatted)) {
      return formatted;
    }
    return ((givenName == null ? "" : givenName) + " " + (familyName == null ? "" : familyName))
        .trim();
  }

  /** The primary email, else the first one. */
  private static String resolveEmail(final List<Email> emails) {
    if (emails == null || emails.isEmpty()) {
      return "";
    }
    final var selected =
        emails.stream()
            .filter(email -> Boolean.TRUE.equals(email.getPrimary()))
            .findFirst()
            .orElse(emails.getFirst());
    return selected.getValue() == null ? "" : selected.getValue();
  }

  private static UserResource toResource(
      final String username,
      final String name,
      final String email,
      final String externalId,
      final boolean active,
      final URI location) {
    final var resource = new UserResource();
    resource.setId(username);
    resource.setUserName(username);
    resource.setActive(active);
    resource.setSchemaUrns(USER_SCHEMA);
    resource.setMeta(new Meta().setResourceType("User").setLocation(location));
    if (isNotBlank(externalId)) {
      resource.setExternalId(externalId);
    }
    if (isNotBlank(name)) {
      resource.setDisplayName(name);
      resource.setName(new Name().setFormatted(name));
    }
    if (isNotBlank(email)) {
      resource.setEmails(new Email().setValue(email).setType(WORK_EMAIL_TYPE).setPrimary(true));
    }
    return resource;
  }

  private static boolean isNotBlank(final String value) {
    return value != null && !value.isBlank();
  }
}
