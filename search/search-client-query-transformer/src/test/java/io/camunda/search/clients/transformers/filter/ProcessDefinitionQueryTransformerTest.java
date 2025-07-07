/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.AuthorizationResult;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.security.resource.TenantResult;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ProcessDefinitionQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByProcessDefinitionKey() {
    final var filter = FilterBuilders.processDefinition(f -> f.processDefinitionKeys(1L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(1L);
            });
  }

  @Test
  public void shouldQueryByName() {
    final var filter = FilterBuilders.processDefinition(f -> f.names("Order process"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("name");
              assertThat(t.value().stringValue()).isEqualTo("Order process");
            });
  }

  @Test
  public void shouldQueryByProcessDefinitionId() {
    final var filter =
        FilterBuilders.processDefinition(f -> f.processDefinitionIds("complexProcess"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().stringValue()).isEqualTo("complexProcess");
            });
  }

  @Test
  public void shouldQueryByVersion() {
    final var filter = FilterBuilders.processDefinition(f -> f.versions(5));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("version");
              assertThat(t.value().intValue()).isEqualTo(5);
            });
  }

  @Test
  public void shouldQueryByVersionTag() {
    final var filter = FilterBuilders.processDefinition(f -> f.versionTags("alpha"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("versionTag");
              assertThat(t.value().stringValue()).isEqualTo("alpha");
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    final var filter = FilterBuilders.processDefinition(f -> f.tenantIds("<default>"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("<default>");
            });
  }

  @Test
  public void shouldQueryByResourceName() {
    final var filter =
        FilterBuilders.processDefinition(f -> f.resourceNames("usertest/single-task.bpmn"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("resourceName");
              assertThat(t.value().stringValue()).isEqualTo("usertest/single-task.bpmn");
            });
  }

  @Test
  public void shouldApplyAuthorizationFilterWithResourceIds() {
    // given
    final var filter = FilterBuilders.processDefinition(b -> b);
    final var expectedAuthorization =
        Authorization.of(
            a ->
                a.resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
                    .permissionType(PermissionType.READ)
                    .resourceIds(List.of("123")));
    final var authorizationFilter =
        AuthorizationResult.requiredAuthorizationCheck(expectedAuthorization);

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.authorizationResult(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().value()).isEqualTo("123");
            });
  }

  @Test
  public void shouldApplyAuthorizationFilterWithGranted() {
    // given
    final var filter = FilterBuilders.processDefinition(b -> b);
    final var authorizationFilter = AuthorizationResult.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.authorizationResult(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyAuthorizationFilterWithForbidden() {
    // given
    final var filter = FilterBuilders.processDefinition(b -> b);
    final var authorizationFilter = AuthorizationResult.unsuccessful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.authorizationResult(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldApplyTenantFilterWithGranted() {
    // given
    final var filter = FilterBuilders.processDefinition(b -> b);
    final var tenantFilter = TenantResult.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.tenantResult(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyTenantFilterWithForbidden() {
    // given
    final var filter = FilterBuilders.processDefinition(b -> b);
    final var tenantFilter = TenantResult.unsuccessful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.tenantResult(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldIgnoreTenantFilterWithTenantIds() {
    // given
    final var filter = FilterBuilders.processDefinition(b -> b);
    final var tenantFilter = TenantResult.tenantCheckRequired(List.of("bar"));

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.tenantResult(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().value()).isEqualTo("bar");
            });
  }

  @Test
  public void shouldApplyAllFilters() {
    // given
    final var filter = FilterBuilders.processDefinition(b -> b.names("foo"));
    final var authorizationFilter = AuthorizationResult.successful();
    final var tenantFilter = TenantResult.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter,
            ResourceAccessResult.of(
                b -> b.authorizationResult(authorizationFilter).tenantResult(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final var must = ((SearchBoolQuery) queryVariant).must();
    assertThat(must).hasSize(3);
  }
}
