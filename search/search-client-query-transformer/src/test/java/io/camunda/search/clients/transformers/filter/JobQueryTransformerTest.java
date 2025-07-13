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
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JobQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByJobKey() {
    // Given
    final var filter = FilterBuilders.job(b -> b.jobKeys(123L));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(123L);
            });
  }

  @Test
  public void shouldQueryByJobType() {
    // Given
    final var filter = FilterBuilders.job(b -> b.types("externalTask"));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("externalTask");
            });
  }

  @Test
  public void shouldQueryByJobWorker() {
    // Given a JobFilter with job workers
    final var filter = FilterBuilders.job(b -> b.workers("worker1"));

    // When transforming the filter to a search query
    final var searchQuery = transformQuery(filter);

    // Then the search query should contain the correct job worker condition
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("worker");
              assertThat(t.value().stringValue()).isEqualTo("worker1");
            });
  }

  @Test
  public void shouldQueryByJobState() {
    // Given a JobFilter with job states
    final var filter =
        FilterBuilders.job(
            b ->
                b.stateOperations(
                    Operation.in(JobState.CREATED.name(), JobState.COMPLETED.name())));

    // When transforming the filter to a search query
    final var searchQuery = transformQuery(filter);

    // Then the search query should contain the correct job state condition
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.values())
                  .extracting("stringValue")
                  .containsExactlyInAnyOrder("CREATED", "COMPLETED");
            });
  }

  @Test
  public void shouldQueryByKind() {
    // Given
    final var filter =
        FilterBuilders.job(b -> b.kindOperations(Operation.neq(JobKind.TASK_LISTENER.name())));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            t -> {
              assertThat(t.mustNot()).hasSize(1);
              assertThat(t.mustNot().getFirst())
                  .isInstanceOfSatisfying(
                      SearchQuery.class,
                      query -> {
                        assertThat(query.queryOption())
                            .isInstanceOfSatisfying(
                                SearchTermQuery.class,
                                term -> {
                                  assertThat(term.field()).isEqualTo("jobKind");
                                  assertThat(term.value().stringValue())
                                      .isEqualTo(JobKind.TASK_LISTENER.name());
                                });
                      });
            });
  }

  @Test
  public void shouldQueryByListenerEventType() {
    // Given
    final var filter =
        FilterBuilders.job(b -> b.listenerEventTypes(ListenerEventType.START.name()));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("listenerEventType");
              assertThat(t.value().stringValue()).isEqualTo("START");
            });
  }

  @Test
  public void shouldQueryByProcessDefinitionId() {
    // Given
    final var filter = FilterBuilders.job(b -> b.processDefinitionIds("process_123"));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().stringValue()).isEqualTo("process_123");
            });
  }

  @Test
  public void shouldQueryByProcessDefinitionKey() {
    // Given
    final var filter = FilterBuilders.job(b -> b.processDefinitionKeys(456L));
    // When
    final var searchQuery = transformQuery(filter);
    // Then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionKey");
              assertThat(t.value().longValue()).isEqualTo(456L);
            });
  }

  @Test
  public void shouldQueryByProcessInstanceKey() {
    // Given
    final var filter = FilterBuilders.job(b -> b.processInstanceKeys(789L));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(789L);
            });
  }

  @Test
  public void shouldQueryByElementId() {
    // Given
    final var filter = FilterBuilders.job(b -> b.elementIds("task_1"));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("flowNodeId");
              assertThat(t.value().stringValue()).isEqualTo("task_1");
            });
  }

  @Test
  public void shouldQueryByElementInstanceKey() {
    // Given
    final var filter = FilterBuilders.job(b -> b.elementInstanceKeys(101L));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("flowNodeInstanceId");
              assertThat(t.value().longValue()).isEqualTo(101L);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // Given
    final var filter = FilterBuilders.job(b -> b.tenantIds("tenant_1"));

    // When
    final var searchQuery = transformQuery(filter);

    // Then
    final var queryVariant = searchQuery.queryOption();

    assertThat(queryVariant)
        .isNotNull()
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("tenant_1");
            });
  }

  @Test
  public void shouldApplyAuthorizationCheck() {
    // given
    final var authorization =
        Authorization.of(
            a -> a.processDefinition().readProcessInstance().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.job(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(JobTemplate.BPMN_PROCESS_ID);
              assertThat(t.values()).hasSize(2);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("1", "2");
            });
  }

  @Test
  public void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessInstance());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.job(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldIgnoreAuthorizationCheckWhenDisabled() {
    // given
    final var authorizationCheck = AuthorizationCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery = transformQuery(FilterBuilders.job(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldApplyTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery = transformQuery(FilterBuilders.job(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(JobTemplate.TENANT_ID);
              assertThat(t.values()).hasSize(2);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("a", "b");
            });
  }

  @Test
  public void shouldIgnoreTenantCheckWhenDisabled() {
    // given
    final var tenantCheck = TenantCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery = transformQuery(FilterBuilders.job(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldApplyFilterAndChecks() {
    // given
    final var authorization =
        Authorization.of(
            a -> a.processDefinition().readProcessInstance().resourceIds(List.of("1", "2")));

    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.job(b -> b.elementIds("abc")), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(SearchBoolQuery.class, t -> assertThat(t.must()).hasSize(3));
  }
}
