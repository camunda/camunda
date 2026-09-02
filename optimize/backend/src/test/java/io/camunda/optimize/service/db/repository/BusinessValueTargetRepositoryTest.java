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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.repository.es.BusinessValueTargetRepositoryES;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessValueTargetRepositoryTest {

  /**
   * An empty authorized-tenant list means the caller may see nothing, and it must not be allowed to
   * fall through to the unfiltered read that {@code null} selects. Verified by asserting the client
   * is never touched rather than that the result is empty — a query that ran and happened to match
   * nothing would satisfy the weaker assertion while still having read every tenant's targets.
   */
  @Test
  void shouldReadNothingWithoutQueryingWhenTheCallerSeesNoTenants() {
    // given
    final OptimizeElasticsearchClient esClient = mock(OptimizeElasticsearchClient.class);
    final BusinessValueTargetRepositoryES repository =
        new BusinessValueTargetRepositoryES(esClient, new ObjectMapper());

    // when
    final List<BusinessValueTargetDto> targets = repository.readByTenants(List.of());

    // then
    assertThat(targets).isEmpty();
    verifyNoInteractions(esClient);
  }

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
