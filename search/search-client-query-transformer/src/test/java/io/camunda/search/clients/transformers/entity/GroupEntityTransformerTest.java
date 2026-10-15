/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.usermanagement.GroupEntity;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class GroupEntityTransformerTest {

  private final GroupEntityTransformer transformer = new GroupEntityTransformer();

  @Test
  void shouldMapExternalId() {
    // given
    final var source =
        new GroupEntity().setKey(1L).setGroupId("group").setName("Group").setExternalId("ext-1");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.externalId()).isEqualTo("ext-1");
  }

  @Test
  void shouldMapSource() {
    // given
    final var source =
        new GroupEntity().setKey(1L).setGroupId("group").setName("Group").setSource("scim-a");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.source()).isEqualTo("scim-a");
  }

  @Test
  void shouldKeepSourceNullForDocumentsWrittenBeforeTheField() {
    // given
    final var source = new GroupEntity().setKey(1L).setGroupId("group").setName("Group");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.source()).isNull();
  }

  @Test
  void shouldKeepExternalIdNullForDocumentsWrittenBeforeTheField() {
    // given
    final var source = new GroupEntity().setKey(1L).setGroupId("group").setName("Group");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.externalId()).isNull();
  }

  @Test
  void shouldParseAttributesJson() {
    // given
    final var source =
        new GroupEntity()
            .setKey(1L)
            .setGroupId("group")
            .setName("Group")
            .setAttributes("{\"costCenter\":\"42\"}");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.attributes()).containsEntry("costCenter", "42");
  }

  @Test
  void shouldMapMissingAttributesToNull() {
    // given
    final var source = new GroupEntity().setKey(1L).setGroupId("group").setName("Group");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.attributes()).isNull();
  }

  @Test
  void shouldMapCreatedAtAndLastModifiedAt() {
    // given
    final var createdAt = OffsetDateTime.parse("2026-10-01T10:00:00Z");
    final var lastModifiedAt = OffsetDateTime.parse("2026-10-02T12:30:00Z");
    final var source =
        new GroupEntity()
            .setKey(1L)
            .setGroupId("group")
            .setName("Group")
            .setCreatedAt(createdAt)
            .setLastModifiedAt(lastModifiedAt);

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.lastModifiedAt()).isEqualTo(lastModifiedAt);
  }

  @Test
  void shouldKeepTimestampsNullForDocumentsWrittenBeforeTheFields() {
    // given
    final var source = new GroupEntity().setKey(1L).setGroupId("group").setName("Group");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.createdAt()).isNull();
    assertThat(result.lastModifiedAt()).isNull();
  }
}
