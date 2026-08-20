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

import io.camunda.optimize.service.util.importing.ZeebeConstants;
import org.junit.jupiter.api.Test;

class BusinessValueTargetRepositoryTest {

  @Test
  void shouldCombineTenantAndProcessKeyIntoDocumentId() {
    assertThat(
            BusinessValueTargetRepository.documentId(
                ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, "invoice-automation"))
        .isEqualTo(ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID + "::invoice-automation");
  }

  @Test
  void shouldReturnDifferentDocumentIdsForDifferentTenants() {
    final String a = BusinessValueTargetRepository.documentId("tenant-a", "invoice-automation");
    final String b = BusinessValueTargetRepository.documentId("tenant-b", "invoice-automation");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void shouldRejectNullTenant() {
    assertThatThrownBy(() -> BusinessValueTargetRepository.documentId(null, "any-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void shouldRejectNullProcessKey() {
    assertThatThrownBy(
            () ->
                BusinessValueTargetRepository.documentId(
                    ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionKey");
  }

  @Test
  void shouldRejectBlankTenant() {
    assertThatThrownBy(() -> BusinessValueTargetRepository.documentId("   ", "any-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void shouldRejectBlankProcessKey() {
    assertThatThrownBy(
            () ->
                BusinessValueTargetRepository.documentId(
                    ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionKey");
  }
}
