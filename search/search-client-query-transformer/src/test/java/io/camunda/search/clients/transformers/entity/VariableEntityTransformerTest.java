/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.VariableEntity.ProtectionMode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VariableEntityTransformerTest {

  private final VariableEntityTransformer transformer = new VariableEntityTransformer();

  private io.camunda.webapps.schema.entities.VariableEntity buildSource() {
    return new io.camunda.webapps.schema.entities.VariableEntity()
        .setKey(100L)
        .setName("cardNumber")
        .setValue("value")
        .setIsPreview(false)
        .setScopeKey(200L)
        .setProcessInstanceKey(300L)
        .setBpmnProcessId("myProcess")
        .setTenantId("<default>");
  }

  @Test
  void shouldMapProtectionModes() {
    // given
    final var source =
        buildSource()
            .setProtectionModes(
                Set.of(io.camunda.zeebe.protocol.record.value.ProtectionMode.REDACT));

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.protectionModes()).containsExactly(ProtectionMode.REDACT);
  }

  @Test
  void shouldMapMissingProtectionModesToEmpty() {
    // given a variable indexed before protection modes existed
    final var source = buildSource();

    // when
    final var result = transformer.apply(source);

    // then
    assertThat(result.protectionModes()).isEmpty();
  }
}
