/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.security.reader.TenantAccess;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.es.tenant.ElasticsearchEs8TenantCheckApplier;
import io.camunda.tasklist.webapp.tenant.TenantService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchEs8TenantCheckApplierTest {

  @Mock private TenantService tenantService;

  @InjectMocks private ElasticsearchEs8TenantCheckApplier instance;

  @Test
  void checkIfQueryContainsTenant() {
    // given
    final Query originalQuery = ElasticsearchUtil.termsQuery("test", "1");
    final var tenantAccess = mock(TenantAccess.class);
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(tenantAccess.allowed()).thenReturn(true);
    when(tenantAccess.tenantIds()).thenReturn(authorizedTenant);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    final Query result = instance.apply(originalQuery);

    // then
    assertThat(result).isNotNull();
    assertThat(result.isBool()).isTrue();
    assertThat(result.bool().must()).hasSize(2);

    // Verify tenant query is present
    final String resultString = result.toString();
    assertThat(resultString).contains("tenantId");
    assertThat(resultString).contains("TenantA");
    assertThat(resultString).contains("TenantB");
    // Verify original query is preserved
    assertThat(resultString).contains("test");
  }

  @Test
  void checkIfQueryContainsAccessibleTenantsProvidedByUser() {
    // given
    final Query originalQuery = ElasticsearchUtil.termsQuery("test", "1");
    final var tenantAccess = mock(TenantAccess.class);
    final List<String> tenantsProvidedByUser = List.of("TenantA", "TenantC");
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(tenantAccess.allowed()).thenReturn(true);
    when(tenantAccess.tenantIds()).thenReturn(authorizedTenant);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    final Query result = instance.apply(originalQuery, tenantsProvidedByUser);

    // then
    assertThat(result).isNotNull();
    assertThat(result.isBool()).isTrue();
    assertThat(result.bool().must()).hasSize(2);

    // Verify only the intersection tenant (TenantA) is present, not TenantC (not authorized)
    final String resultString = result.toString();
    assertThat(resultString).contains("tenantId");
    assertThat(resultString).contains("TenantA");
    assertThat(resultString).doesNotContain("TenantC");
    // TenantB is authorized but not requested by user
    assertThat(resultString).doesNotContain("TenantB");
    // Verify original query is preserved
    assertThat(resultString).contains("test");
  }

  @Test
  void checkShouldReturnNoneMatchQueryIfUserProvidedNotAccessibleTenants() {
    // given
    final Query originalQuery = ElasticsearchUtil.termsQuery("test", "1");
    final var tenantAccess = mock(TenantAccess.class);
    final List<String> tenantsProvidedByUser = List.of("UnknownTenant");
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(tenantAccess.tenantIds()).thenReturn(authorizedTenant);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    final Query result = instance.apply(originalQuery, tenantsProvidedByUser);

    // then - should return match_none query
    assertThat(result).isNotNull();
    assertThat(result.isBool()).isTrue();
    assertThat(result.bool().must()).hasSize(1);
    assertThat(result.bool().must().get(0).isMatchNone()).isTrue();
  }

  @Test
  void checkShouldReturnNoneMatchQueryIfUserHaveNoneTenantsAccess() {
    // given
    final Query originalQuery = ElasticsearchUtil.termsQuery("test", "1");
    final var tenantAccess = mock(TenantAccess.class);
    when(tenantAccess.denied()).thenReturn(true);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    final Query result = instance.apply(originalQuery);

    // then - should return match_none query
    assertThat(result).isNotNull();
    assertThat(result.isBool()).isTrue();
    assertThat(result.bool().must()).hasSize(1);
    assertThat(result.bool().must().get(0).isMatchNone()).isTrue();
  }

  @Test
  void checkThatQueryDontContainTenantWhenMultiTenancyIsTurnedOff() {
    // given
    final Query originalQuery = ElasticsearchUtil.termsQuery("test", "1");
    final var tenantAccess = mock(TenantAccess.class);
    when(tenantAccess.wildcard()).thenReturn(true);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    final Query result = instance.apply(originalQuery);

    // then - query should be unchanged (no tenant filter added)
    assertThat(result).isNotNull();
    assertThat(result.isTerms()).isTrue();
    final String resultString = result.toString();
    assertThat(resultString).doesNotContain("tenantId");
    assertThat(resultString).contains("test");
  }
}
