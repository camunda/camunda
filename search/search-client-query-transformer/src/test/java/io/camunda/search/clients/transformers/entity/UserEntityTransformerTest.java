/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.usermanagement.UserEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserEntityTransformerTest {

  private final UserEntityTransformer transformer = new UserEntityTransformer();

  @Test
  void shouldMapExternalIdAndActive() {
    // given
    final var source =
        new UserEntity().setKey(1L).setUsername("user").setExternalId("ext-1").setActive(false);

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.externalId()).isEqualTo("ext-1");
    assertThat(result.active()).isFalse();
  }

  @Test
  void shouldMapSource() {
    // given
    final var source = new UserEntity().setKey(1L).setUsername("user").setSource("scim-a");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.source()).isEqualTo("scim-a");
  }

  @Test
  void shouldKeepSourceNullForDocumentsWrittenBeforeTheField() {
    // given
    final var source = new UserEntity().setKey(1L).setUsername("user");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.source()).isNull();
  }

  @Test
  void shouldKeepActiveNullForDocumentsWrittenBeforeTheField() {
    // given
    final var source = new UserEntity().setKey(1L).setUsername("user");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.externalId()).isNull();
    assertThat(result.active()).isNull();
  }

  @Test
  void shouldParseAttributesJson() {
    // given
    final var source =
        new UserEntity()
            .setKey(1L)
            .setUsername("user")
            .setAttributes("{\"department\":\"eng\",\"levels\":[1,2]}");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.attributes())
        .containsEntry("department", "eng")
        .containsEntry("levels", List.of(1, 2));
  }

  @Test
  void shouldMapMissingAttributesToNull() {
    // given
    final var source = new UserEntity().setKey(1L).setUsername("user");

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
        new UserEntity()
            .setKey(1L)
            .setUsername("alice")
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
    final var source = new UserEntity().setKey(1L).setUsername("alice");

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.createdAt()).isNull();
    assertThat(result.lastModifiedAt()).isNull();
  }
}
