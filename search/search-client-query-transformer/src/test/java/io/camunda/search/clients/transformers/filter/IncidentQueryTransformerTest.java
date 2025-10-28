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
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class IncidentQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByIncidentKey() {
    final var filter = FilterBuilders.incident(f -> f.incidentKeys(1L));

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
  public void shouldQueryByProcessDefinitionKey() {
    final var filter = FilterBuilders.incident(f -> f.processDefinitionKeys(5432L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionKey");
              assertThat(t.value().longValue()).isEqualTo(5432L);
            });
  }

  @Test
  public void shouldQueryByBpmnProcessId() {
    final var filter = FilterBuilders.incident(f -> f.processDefinitionIds("complexProcess"));

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
  public void shouldQueryByProcessInstanceKey() {
    final var filter = FilterBuilders.incident(f -> f.processInstanceKeys(42L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(42L);
            });
  }

  @Test
  public void shouldQueryByErrorType() {
    final var filter = FilterBuilders.incident(f -> f.errorTypes(ErrorType.JOB_NO_RETRIES.name()));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("errorType");
              assertThat(t.value().stringValue()).isEqualTo("JOB_NO_RETRIES");
            });
  }

  @Test
  public void shouldQueryByResourceNotFoundErrorType() {
    final var filter =
        FilterBuilders.incident(f -> f.errorTypes(ErrorType.RESOURCE_NOT_FOUND.name()));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("errorType");
              assertThat(t.value().stringValue()).isEqualTo("RESOURCE_NOT_FOUND");
            });
  }

  @Test
  public void shouldQueryByErrorMessage() {
    final var filter = FilterBuilders.incident(f -> f.errorMessages("No retries left."));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("errorMessage");
              assertThat(t.value().stringValue()).isEqualTo("No retries left.");
            });
  }

  @Test
  public void shouldQueryByFlowNodeId() {
    final var filter = FilterBuilders.incident(f -> f.flowNodeIds("flowNodeId-17"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("flowNodeId");
              assertThat(t.value().stringValue()).isEqualTo("flowNodeId-17");
            });
  }

  @Test
  public void shouldQueryByFlowNodeInstanceKey() {
    final var filter = FilterBuilders.incident(f -> f.flowNodeInstanceKeys(42L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("flowNodeInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(42L);
            });
  }

  @Test
  public void shouldQueryByCreationTime() {
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    final var date = OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
    final var filter = FilterBuilders.incident(f -> f.creationTime(date));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("creationTime");
              assertThat(t.value().stringValue()).isEqualTo(date.format(formatter));
            });
  }

  @Test
  public void shouldQueryByState() {
    final var filter = FilterBuilders.incident(f -> f.states(IncidentState.ACTIVE.name()));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo("ACTIVE");
            });
  }

  @Test
  public void shouldQueryByJobKey() {
    final var filter = FilterBuilders.incident(f -> f.jobKeys(23L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("jobKey");
              assertThat(t.value().longValue()).isEqualTo(23L);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    final var filter = FilterBuilders.incident(f -> f.tenantIds("Homer"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("Homer");
            });
  }

  @Test
  public void shouldQueryByIncidentErrorHashCode() {
    final var filter = FilterBuilders.incident(f -> f.errorMessageHashes(123456780));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("errorMessageHash");
              assertThat(t.value().intValue()).isEqualTo(123456780);
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
    final var searchQuery = transformQuery(FilterBuilders.incident(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(IncidentTemplate.BPMN_PROCESS_ID);
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
    final var searchQuery = transformQuery(FilterBuilders.incident(b -> b), resourceAccessChecks);

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
    final var searchQuery = transformQuery(FilterBuilders.incident(b -> b), resourceAccessChecks);

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
    final var searchQuery = transformQuery(FilterBuilders.incident(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(IncidentTemplate.TENANT_ID);
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
    final var searchQuery = transformQuery(FilterBuilders.incident(b -> b), resourceAccessChecks);

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
        transformQuery(FilterBuilders.incident(b -> b.flowNodeIds("abc")), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(SearchBoolQuery.class, t -> assertThat(t.must()).hasSize(3));
  }

  @Test
  public void shouldQueryByAdvancedProcessInstanceKeyFilter() {
    final var filter =
        FilterBuilders.incident(f -> f.processInstanceKeyOperations(Operation.eq(42L)));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(42L);
            });
  }

  @Test
  public void shouldQueryByAdvancedIncidentErrorTypeFilter() {
    final var filter =
        FilterBuilders.incident(
            f ->
                f.errorTypeOperations(
                    Operation.in(
                        List.of(
                            ErrorType.JOB_NO_RETRIES.name(),
                            ErrorType.RESOURCE_NOT_FOUND.name()))));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("errorType");
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("JOB_NO_RETRIES", "RESOURCE_NOT_FOUND");
            });
  }

  @Test
  public void shouldQueryByAdvancedIncidentStateFilter() {
    final var filter =
        FilterBuilders.incident(
            f ->
                f.stateOperations(
                    Operation.notIn(
                        List.of(IncidentState.ACTIVE.name(), IncidentState.RESOLVED.name()))));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            t -> {
              assertThat(t.mustNot()).hasSize(1);
              final var mustNotQuery = t.mustNot().getFirst();
              assertThat(mustNotQuery.queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermsQuery.class,
                      termsQuery -> {
                        assertThat(termsQuery.field()).isEqualTo("state");
                        assertThat(
                                termsQuery.values().stream().map(TypedValue::stringValue).toList())
                            .containsExactlyInAnyOrder("ACTIVE", "RESOLVED");
                      });
            });
  }
}
