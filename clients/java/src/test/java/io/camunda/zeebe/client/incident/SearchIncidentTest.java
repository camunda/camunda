/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.protocol.rest.IncidentFilterRequest;
import io.camunda.zeebe.client.protocol.rest.IncidentSearchQueryRequest;
import io.camunda.zeebe.client.protocol.rest.SearchQuerySortRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SearchIncidentTest extends ClientRestTest {

  @Test
  public void shouldSearchIncident() {
    // when
    client.newIncidentQuery().send().join();

    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  public void shouldSearchIncidentWithFullFilters() {
    // when
    client
        .newIncidentQuery()
        .filter(
            f ->
                f.key(1L)
                    .processDefinitionKey(2L)
                    .processInstanceKey(3L)
                    .tenantId("tenant")
                    .flowNodeId("flowNode")
                    .flowNodeInstanceId("flowNodeInstance")
                    .jobKey(4l)
                    .state("state")
                    .type("type")
                    .hasActiveOperation(false))
        .send()
        .join();
    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    final IncidentFilterRequest filter = request.getFilter();
    assertThat(filter.getKey()).isEqualTo(1L);
    assertThat(filter.getProcessDefinitionKey()).isEqualTo(2L);
    assertThat(filter.getProcessInstanceKey()).isEqualTo(3L);
    assertThat(filter.getTenantId()).isEqualTo("tenant");
    assertThat(filter.getFlowNodeId()).isEqualTo("flowNode");
    assertThat(filter.getFlowNodeInstanceId()).isEqualTo("flowNodeInstance");
    assertThat(filter.getJobKey()).isEqualTo(4l);
    assertThat(filter.getState()).isEqualTo("state");
    assertThat(filter.getType()).isEqualTo("type");
    assertThat(filter.getHasActiveOperation()).isFalse();
  }

  @Test
  public void shouldSearchIncidentWithFullSorting() {
    // when
    client
        .newIncidentQuery()
        .sort(
            s ->
                s.key()
                    .creationTime()
                    .jobKey()
                    .flowNodeId()
                    .state()
                    .flowNodeInstanceId()
                    .processDefinitionKey()
                    .processInstanceKey()
                    .tenantId()
                    .type())
        .send()
        .join();
    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    final List<SearchQuerySortRequest> sort = request.getSort();
    assertThat(sort.size()).isEqualTo(9);
    assertThat(sort.get(0).getField()).isEqualTo("key");
    assertThat(sort.get(0).getOrder()).isEqualTo("asc");
  }
}
