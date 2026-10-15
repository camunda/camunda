/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.usermanagement.MappingRuleEntity;
import org.junit.jupiter.api.Test;

class MappingRuleEntityTransformerTest {

  private final MappingRuleEntityTransformer transformer = new MappingRuleEntityTransformer();

  @Test
  void shouldMapType() {
    // given
    final var type = baseEntity().setType("USER_ATTRIBUTE");

    // when
    final var result = transformer.apply(type);

    // then
    assertThat(result.type()).isEqualTo("USER_ATTRIBUTE");
  }

  @Test
  void shouldDefaultTypeToTokenClaimForDocumentsWrittenBeforeTheField() {
    // given
    final var type = baseEntity();

    // when
    final var result = transformer.apply(type);

    // then
    assertThat(result.type()).isEqualTo("TOKEN_CLAIM");
  }

  private static MappingRuleEntity baseEntity() {
    return new MappingRuleEntity()
        .setMappingRuleId("rule")
        .setKey(1L)
        .setClaimName("claim")
        .setClaimValue("value")
        .setName("Rule");
  }
}
