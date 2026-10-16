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
import static org.assertj.core.api.Assertions.tuple;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import com.unboundid.scim2.common.types.GroupResource;
import io.camunda.gateway.scim.ScimJson;
import io.camunda.search.entities.GroupEntity;
import io.camunda.service.GroupServices.GroupAttribute;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScimGroupMapperTest {

  private static final URI LOCATION = URI.create("http://localhost/scim/v2/Groups/eng");

  private final ScimGroupMapper mapper = new ScimGroupMapper();

  @Test
  void shouldMapCreateRequestWithoutTouchingTheDescription() throws Exception {
    // given
    final var resource =
        ScimJson.read(
            """
            {"schemas":["urn:ietf:params:scim:schemas:core:2.0:Group"],
             "displayName":"Engineering","externalId":"ext-1"}
            """,
            GroupResource.class);

    // when
    final var dto = mapper.toCreateDto(resource, "engineering");

    // then
    assertThat(dto).isEqualTo(new GroupDTO("engineering", "Engineering", "", "ext-1"));
  }

  @Test
  void shouldRequireDisplayName() throws Exception {
    // given
    final var resource = ScimJson.read("{\"externalId\":\"x\"}", GroupResource.class);

    // when / then
    assertThatThrownBy(() -> mapper.toCreateDto(resource, "x"))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidValue"));
  }

  @Test
  void shouldNeverReplaceTheDescription() {
    // then
    assertThat(ScimGroupMapper.REPLACED_ATTRIBUTES)
        .containsExactlyInAnyOrder(GroupAttribute.NAME, GroupAttribute.EXTERNAL_ID);
  }

  @Test
  void shouldCollectDistinctMemberUsernamesInOrder() throws Exception {
    // given
    final var resource =
        ScimJson.read(
            """
            {"displayName":"x","members":[{"value":"bob"},{"value":"alice","type":"User"},
             {"value":"bob"}]}
            """,
            GroupResource.class);

    // when
    final var members = mapper.memberIds(resource.getMembers());

    // then
    assertThat(members).containsExactly("bob", "alice");
  }

  @Test
  void shouldRejectNestedGroupMember() throws Exception {
    // given
    final var resource =
        ScimJson.read(
            "{\"displayName\":\"x\",\"members\":[{\"value\":\"g1\",\"type\":\"Group\"}]}",
            GroupResource.class);

    // when / then
    assertThatThrownBy(() -> mapper.memberIds(resource.getMembers()))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidValue"));
  }

  @Test
  void shouldRejectMemberWithoutValue() throws Exception {
    // given
    final var resource =
        ScimJson.read(
            "{\"displayName\":\"x\",\"members\":[{\"display\":\"a\"}]}", GroupResource.class);

    // when / then
    assertThatThrownBy(() -> mapper.memberIds(resource.getMembers()))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void shouldMapRecordWithMembersAndReferences() {
    // given
    final var record =
        new GroupRecord().setGroupId("eng").setName("Engineering").setExternalId("ext-1");

    // when
    final var resource =
        mapper.toResource(
            record,
            List.of("alice", "bob@x.io"),
            LOCATION,
            username -> URI.create("http://localhost/scim/v2/Users/" + username));

    // then
    assertThat(resource.getId()).isEqualTo("eng");
    assertThat(resource.getDisplayName()).isEqualTo("Engineering");
    assertThat(resource.getExternalId()).isEqualTo("ext-1");
    assertThat(resource.getSchemaUrns()).containsExactly(ScimGroupMapper.GROUP_SCHEMA);
    assertThat(resource.getMeta().getResourceType()).isEqualTo("Group");
    assertThat(resource.getMeta().getLocation()).isEqualTo(LOCATION);
    assertThat(resource.getMembers())
        .extracting(m -> m.getValue(), m -> m.getRef().toString())
        .containsExactly(
            tuple("alice", "http://localhost/scim/v2/Users/alice"),
            tuple("bob@x.io", "http://localhost/scim/v2/Users/bob@x.io"));
  }

  @Test
  void shouldTreatEmptyExternalIdOfEntityAsAbsentAndOmitMembersWhenUnknown() {
    // given
    final var entity = new GroupEntity(1L, "eng", "Engineering", "desc", "");

    // when
    final var resource = mapper.toResource(entity, null, LOCATION, username -> LOCATION);

    // then
    assertThat(resource.getExternalId()).isNull();
    assertThat(resource.getMembers()).isNull();
    assertThat(resource.getDisplayName()).isEqualTo("Engineering");
  }

  @Test
  void shouldExposeCreatedAndLastModifiedOfStoredEntityAsMeta() {
    // given
    final var created = OffsetDateTime.parse("2026-10-01T10:00:00Z");
    final var lastModified = OffsetDateTime.parse("2026-10-02T12:30:00Z");
    final var entity =
        new GroupEntity(1L, "eng", "Engineering", "desc", "", null, created, lastModified);

    // when
    final var resource = mapper.toResource(entity, null, LOCATION, username -> LOCATION);

    // then
    assertThat(resource.getMeta().getCreated().toInstant()).isEqualTo(created.toInstant());
    assertThat(resource.getMeta().getLastModified().toInstant())
        .isEqualTo(lastModified.toInstant());
  }
}
