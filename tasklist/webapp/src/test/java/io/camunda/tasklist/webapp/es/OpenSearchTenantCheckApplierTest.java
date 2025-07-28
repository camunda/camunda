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

import io.camunda.security.reader.TenantAccess;
import io.camunda.tasklist.webapp.es.tenant.OpenSearchTenantCheckApplier;
import io.camunda.tasklist.webapp.tenant.TenantService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.MatchNoneQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;

@ExtendWith(MockitoExtension.class)
public class OpenSearchTenantCheckApplierTest {

  @Mock private TenantService tenantService;

  @InjectMocks private OpenSearchTenantCheckApplier instance;

  @Test
  void checkIfQueryContainsTenant() {
    // given
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest
        .index("test")
        .query(q -> q.term(term -> term.value(FieldValue.of("value")).field("field")));

    final var tenantAccess = mock(TenantAccess.class);
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(tenantAccess.allowed()).thenReturn(true);
    when(tenantAccess.tenantIds()).thenReturn(authorizedTenant);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    instance.apply(searchRequest);

    // then
    final SearchRequest sr = searchRequest.build();
    assertThat(sr.query()._kind()).isEqualTo(Query.Kind.Bool);
    assertThat(sr.query().bool().must()).hasSize(2);
    assertThat(sr.query().bool().must().get(0).terms().field()).isEqualTo("tenantId");
    assertThat(sr.query().bool().must().get(0).terms().terms().value())
        .map(FieldValue::stringValue)
        .containsExactly("TenantA", "TenantB");
    assertThat(sr.query().bool().must().get(1).term().value().stringValue()).isEqualTo("value");
    assertThat(sr.query().bool().must().get(1).term().field()).isEqualTo("field");
  }

  @Test
  void checkShouldReturnNoneMatchQueryIfUserProvidedNotAccessibleTenants() {
    // given
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest
        .index("test")
        .query(q -> q.term(term -> term.value(FieldValue.of("value")).field("field")));

    final var tenantAccess = mock(TenantAccess.class);
    final List<String> tenantsProvidedByUser = List.of("UnknownTenant");
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(tenantAccess.tenantIds()).thenReturn(authorizedTenant);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    instance.apply(searchRequest, tenantsProvidedByUser);

    // then
    final SearchRequest sr = searchRequest.build();
    assertThat(sr.query()._kind()).isEqualTo(Query.Kind.Bool);
    assertThat(sr.query().bool().must()).hasSize(1);
    assertThat(sr.query().bool().must().get(0)._kind().toString()).isEqualTo("MatchNone");
    final var mustClause = sr.query().bool().must().get(0);
    assertThat(mustClause._kind().toString()).isEqualTo("MatchNone");
    assertThat(((MatchNoneQuery) mustClause._get()).queryName()).isEqualTo("matchNone");
  }

  @Test
  void checkIfQueryContainsAccessibleTenantsProvidedByUser() {
    // given
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest
        .index("test")
        .query(q -> q.term(term -> term.value(FieldValue.of("value")).field("field")));

    final var tenantAccess = mock(TenantAccess.class);
    final List<String> tenantsProvidedByUser = List.of("TenantA", "TenantC");
    final List<String> authorizedTenant = List.of("TenantA", "TenantB");
    when(tenantAccess.allowed()).thenReturn(true);
    when(tenantAccess.tenantIds()).thenReturn(authorizedTenant);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    instance.apply(searchRequest, tenantsProvidedByUser);

    // then
    final SearchRequest sr = searchRequest.build();
    assertThat(sr.query()._kind()).isEqualTo(Query.Kind.Bool);
    assertThat(sr.query().bool().must()).hasSize(2);
    assertThat(sr.query().bool().must().get(0).terms().field()).isEqualTo("tenantId");
    assertThat(sr.query().bool().must().get(0).terms().terms().value())
        .map(FieldValue::stringValue)
        .containsExactly("TenantA");
    assertThat(sr.query().bool().must().get(1).term().value().stringValue()).isEqualTo("value");
    assertThat(sr.query().bool().must().get(1).term().field()).isEqualTo("field");
  }

  @Test
  void checkThatQueryDontContainTenantWhenMultiTenancyIsTurnedOff() {
    // given
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest
        .index("test")
        .query(q -> q.term(term -> term.value(FieldValue.of("1")).field("test")));

    final var tenantAccess = mock(TenantAccess.class);
    when(tenantAccess.wildcard()).thenReturn(true);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    instance.apply(searchRequest);

    // then
    final SearchRequest sr = searchRequest.build();
    assertThat(sr.query()._kind()).isEqualTo(Query.Kind.Term);
    assertThat(sr.query().term().value().stringValue()).isEqualTo("1");
    assertThat(sr.query().term().field()).isEqualTo("test");
  }

  @Test
  void checkShouldReturnNoneMatchQueryIfUserHaveNoneTenantsAccess() {
    // given
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest
        .index("test")
        .query(q -> q.term(term -> term.value(FieldValue.of("value")).field("field")));

    final var tenantAccess = mock(TenantAccess.class);
    when(tenantAccess.denied()).thenReturn(true);
    when(tenantService.getAuthenticatedTenants()).thenReturn(tenantAccess);

    // when
    instance.apply(searchRequest);

    // then
    final SearchRequest sr = searchRequest.build();
    assertThat(sr.query()._kind()).isEqualTo(Query.Kind.Bool);
    assertThat(sr.query().bool().must()).hasSize(1);
    assertThat(sr.query().bool().must().get(0)._kind().toString()).isEqualTo("MatchNone");
    final var mustClause = sr.query().bool().must().get(0);
    assertThat(mustClause._kind().toString()).isEqualTo("MatchNone");
    assertThat(((MatchNoneQuery) mustClause._get()).queryName()).isEqualTo("matchNone");
  }
}
