/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.Name;
import com.unboundid.scim2.common.types.UserResource;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.search.entities.UserEntity;
import io.camunda.service.UserServices.UserAttribute;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.net.URI;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ScimUserMapperTest {

  private static final URI LOCATION = URI.create("http://localhost/scim/v2/Users/alice");

  private final ScimUserMapper mapper = new ScimUserMapper();

  @Test
  void shouldMapCreateRequest() throws Exception {
    // given
    final var resource =
        ScimJson.read(
            """
            {"schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
             "userName":"alice","externalId":"ext-1","active":false,"password":"secret",
             "displayName":"Alice Liddell",
             "name":{"givenName":"Alice","familyName":"Other"},
             "emails":[{"value":"home@example.com","type":"home"},
                       {"value":"work@example.com","type":"work","primary":true}]}
            """,
            UserResource.class);

    // when
    final var dto = mapper.toCreateDto(resource);

    // then
    assertThat(dto.username()).isEqualTo("alice");
    assertThat(dto.name()).isEqualTo("Alice Liddell");
    assertThat(dto.email()).isEqualTo("work@example.com");
    assertThat(dto.externalId()).isEqualTo("ext-1");
    assertThat(dto.active()).isFalse();
    assertThat(dto.password()).isEqualTo("secret");
  }

  @Test
  void shouldApplyDefaultsWhenOptionalAttributesAreMissing() throws Exception {
    // given
    final var resource = new UserResource().setUserName("alice");

    // when
    final var dto = mapper.toCreateDto(resource);

    // then
    assertThat(dto.name()).isEmpty();
    assertThat(dto.email()).isEmpty();
    assertThat(dto.password()).isEmpty();
    assertThat(dto.externalId()).isEmpty();
    assertThat(dto.active()).isTrue();
  }

  @Test
  void shouldFallBackToFormattedThenGivenAndFamilyName() throws Exception {
    // given
    final var formatted = new UserResource().setUserName("a").setName(formatted("Ada L."));
    final var parts =
        new UserResource()
            .setUserName("b")
            .setName(new Name().setGivenName("Ada").setFamilyName("Lovelace"));

    // when / then
    assertThat(mapper.toCreateDto(formatted).name()).isEqualTo("Ada L.");
    assertThat(mapper.toCreateDto(parts).name()).isEqualTo("Ada Lovelace");
  }

  @Test
  void shouldUseFirstEmailWhenNoneIsPrimary() throws Exception {
    // given
    final var resource =
        new UserResource()
            .setUserName("alice")
            .setEmails(new Email().setValue("a@x.io"), new Email().setValue("b@x.io"));

    // when / then
    assertThat(mapper.toCreateDto(resource).email()).isEqualTo("a@x.io");
  }

  @Test
  void shouldRejectCreateWithoutUserName() {
    // when / then
    assertThatThrownBy(() -> mapper.toCreateDto(new UserResource()))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidValue"));
  }

  @Test
  void shouldRejectReplaceWithDifferentUserName() {
    // given
    final var resource = new UserResource().setUserName("bob");

    // when / then
    assertThatThrownBy(() -> mapper.toReplaceDto(resource, "alice"))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("mutability"));
  }

  @Test
  void shouldUsePathIdWhenReplaceBodyHasNoUserName() throws Exception {
    // when
    final var dto = mapper.toReplaceDto(new UserResource().setDisplayName("Alice"), "alice");

    // then
    assertThat(dto.username()).isEqualTo("alice");
    assertThat(dto.name()).isEqualTo("Alice");
  }

  @Test
  void shouldReplaceAllMutableAttributesAndPasswordOnlyIfSent() {
    // when / then
    assertThat(mapper.replacedAttributes(new UserResource()))
        .containsExactlyInAnyOrder(
            UserAttribute.NAME,
            UserAttribute.EMAIL,
            UserAttribute.EXTERNAL_ID,
            UserAttribute.ACTIVE);
    assertThat(mapper.replacedAttributes(new UserResource().setPassword("pw")))
        .contains(UserAttribute.PASSWORD);
  }

  @Test
  void shouldMapRecordToResource() {
    // given
    final var record =
        new UserRecord()
            .setUsername("alice")
            .setName("Alice")
            .setEmail("alice@example.com")
            .setExternalId("ext-1")
            .setActive(false);

    // when
    final var resource = mapper.toResource(record, LOCATION);

    // then
    assertThat(resource.getId()).isEqualTo("alice");
    assertThat(resource.getUserName()).isEqualTo("alice");
    assertThat(resource.getDisplayName()).isEqualTo("Alice");
    assertThat(resource.getName().getFormatted()).isEqualTo("Alice");
    assertThat(resource.getEmails())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getValue()).isEqualTo("alice@example.com");
              assertThat(e.getPrimary()).isTrue();
            });
    assertThat(resource.getExternalId()).isEqualTo("ext-1");
    assertThat(resource.getActive()).isFalse();
    assertThat(resource.getSchemaUrns()).containsExactly(ScimUserMapper.USER_SCHEMA);
    assertThat(resource.getMeta().getResourceType()).isEqualTo("User");
    assertThat(resource.getMeta().getLocation()).isEqualTo(LOCATION);
  }

  @Test
  void shouldTreatMissingActiveOfStoredEntityAsActive() {
    // given
    final var entity = new UserEntity(1L, "alice", "Alice", "a@x.io", null);

    // when
    final var resource = mapper.toResource(entity, LOCATION);

    // then
    assertThat(resource.getActive()).isTrue();
    assertThat(resource.getExternalId()).isNull();
  }

  @Test
  void shouldExposeCreatedAndLastModifiedOfStoredEntityAsMeta() {
    // given
    final var created = OffsetDateTime.parse("2026-10-01T10:00:00Z");
    final var lastModified = OffsetDateTime.parse("2026-10-02T12:30:00Z");
    final var entity =
        new UserEntity(
            1L, "alice", "Alice", "a@x.io", null, null, true, null, created, lastModified);

    // when
    final var resource = mapper.toResource(entity, LOCATION);

    // then
    assertThat(resource.getMeta().getCreated().toInstant()).isEqualTo(created.toInstant());
    assertThat(resource.getMeta().getLastModified().toInstant())
        .isEqualTo(lastModified.toInstant());
  }

  @Test
  void shouldLeaveMetaTimestampsUnsetWhenStoredEntityHasNone() {
    // given
    final var entity = new UserEntity(1L, "alice", "Alice", "a@x.io", null);

    // when
    final var resource = mapper.toResource(entity, LOCATION);

    // then
    assertThat(resource.getMeta().getCreated()).isNull();
    assertThat(resource.getMeta().getLastModified()).isNull();
  }

  private static Name formatted(final String value) {
    return new Name().setFormatted(value);
  }
}
