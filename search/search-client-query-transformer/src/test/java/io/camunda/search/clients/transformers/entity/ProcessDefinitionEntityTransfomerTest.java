/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.ProcessEntity;
import org.junit.jupiter.api.Test;

class ProcessDefinitionEntityTransfomerTest {

  private final ProcessDefinitionEntityTransfomer transformer =
      new ProcessDefinitionEntityTransfomer();

  @Test
  void shouldKeepStoredResourceName() {
    // given
    final var document = processDocument().setResourceName("order.bpmn");

    // when
    final var entity = transformer.apply(document);

    // then
    assertThat(entity.resourceName()).isEqualTo("order.bpmn");
  }

  @Test
  void shouldDeriveResourceNameFromBpmnProcessIdWhenMissing() {
    // given - documents written by the camunda/operate#5980 hotfix have no resource name
    final var document = processDocument();

    // when
    final var entity = transformer.apply(document);

    // then
    assertThat(entity.resourceName()).isEqualTo("order-process.bpmn");
  }

  private static ProcessEntity processDocument() {
    return new ProcessEntity()
        .setKey(1L)
        .setBpmnProcessId("order-process")
        .setVersion(1)
        .setTenantId("<default>");
  }
}
