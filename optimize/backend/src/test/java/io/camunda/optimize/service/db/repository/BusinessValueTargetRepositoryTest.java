/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BusinessValueTargetRepositoryTest {

  @Test
  void documentIdCombinesTenantAndProcessKey() {
    assertThat(BusinessValueTargetRepository.documentId("<default>", "invoice-automation"))
        .isEqualTo("<default>::invoice-automation");
  }

  @Test
  void differentTenantsProduceDifferentDocumentIds() {
    final String a = BusinessValueTargetRepository.documentId("tenant-a", "invoice-automation");
    final String b = BusinessValueTargetRepository.documentId("tenant-b", "invoice-automation");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void nullTenantRejected() {
    assertThatThrownBy(() -> BusinessValueTargetRepository.documentId(null, "any-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void nullProcessKeyRejected() {
    assertThatThrownBy(() -> BusinessValueTargetRepository.documentId("<default>", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionKey");
  }
}
