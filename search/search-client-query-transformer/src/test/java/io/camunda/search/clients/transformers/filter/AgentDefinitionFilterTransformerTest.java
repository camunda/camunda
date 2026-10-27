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
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.authz.TenantCheck;
import io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentDefinitionFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByAgentDefinitionKey() {
    final var filter = FilterBuilders.agentDefinition(f -> f.agentDefinitionKeys(100L));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(100L);
            });
  }

  @Test
  void shouldQueryByAgentType() {
    final var filter = FilterBuilders.agentDefinition(f -> f.agentTypes("AI_AGENT_TASK"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("agentType");
              assertThat(t.value().stringValue()).isEqualTo("AI_AGENT_TASK");
            });
  }

  @Test
  void shouldQueryByName() {
    final var filter = FilterBuilders.agentDefinition(f -> f.names("myAgent"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("name");
              assertThat(t.value().stringValue()).isEqualTo("myAgent");
            });
  }

  @Test
  void shouldQueryByElementId() {
    final var filter = FilterBuilders.agentDefinition(f -> f.elementIds("Task_1"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("elementId");
              assertThat(t.value().stringValue()).isEqualTo("Task_1");
            });
  }

  @Test
  void shouldQueryByProcessDefinitionId() {
    final var filter = FilterBuilders.agentDefinition(f -> f.processDefinitionIds("myProcess"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().stringValue()).isEqualTo("myProcess");
            });
  }

  @Test
  void shouldQueryByProcessDefinitionKey() {
    final var filter = FilterBuilders.agentDefinition(f -> f.processDefinitionKeys(400L));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionKey");
              assertThat(t.value().longValue()).isEqualTo(400L);
            });
  }

  @Test
  void shouldQueryByProcessDefinitionVersion() {
    final var filter = FilterBuilders.agentDefinition(f -> f.processDefinitionVersions(2));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionVersion");
              assertThat(t.value().intValue()).isEqualTo(2);
            });
  }

  @Test
  void shouldQueryByProcessDefinitionVersionTag() {
    final var filter = FilterBuilders.agentDefinition(f -> f.processDefinitionVersionTags("v1"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionVersionTag");
              assertThat(t.value().stringValue()).isEqualTo("v1");
            });
  }

  @Test
  void shouldQueryByTenantId() {
    final var filter = FilterBuilders.agentDefinition(f -> f.tenantIds("<default>"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("<default>");
            });
  }

  @Test
  void shouldQueryByAllFields() {
    final var filter =
        FilterBuilders.agentDefinition(
            f ->
                f.agentDefinitionKeys(100L)
                    .agentTypes("AI_AGENT_TASK")
                    .names("myAgent")
                    .elementIds("Task_1")
                    .processDefinitionIds("myProcess")
                    .processDefinitionKeys(400L)
                    .processDefinitionVersions(2)
                    .processDefinitionVersionTags("v1")
                    .tenantIds("<default>"));

    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest.queryOption()).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) searchRequest.queryOption()).must()).hasSize(9);
  }

  @Test
  void shouldApplyAuthorizationCheck() {
    // given
    final var authorization =
        RequiredAuthorization.of(
            a -> a.processDefinition().readProcessDefinition().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.agentDefinition(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(AgentDefinitionIndex.BPMN_PROCESS_ID);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("1", "2");
            });
  }

  @Test
  void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var authorization =
        RequiredAuthorization.of(a -> a.processDefinition().readProcessDefinition());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.agentDefinition(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery.queryOption()).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  void shouldApplyTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.agentDefinition(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(AgentDefinitionIndex.TENANT_ID);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("a", "b");
            });
  }
}
