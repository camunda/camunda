/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.read;

import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.zeebe.read.DataReadMeter.ReadQuery;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class DataReadMeterQueryProvider {

  private DataReadMeterQueryProvider() {}

  public static List<ReadQuery> getDefaultQueries(final Collection<String> disabledQueries) {
    return getDefaultQueries().stream().filter(q -> !disabledQueries.contains(q.name())).toList();
  }

  public static List<ReadQuery> getDefaultQueries() {
    return List.of(
        new ReadQuery(
            "process_instances_active",
            Duration.ofSeconds(30),
            (client, context) ->
                client
                    .newProcessInstanceSearchRequest()
                    .filter(
                        f ->
                            f.processDefinitionId(context.benchmarkProcessDefinitionId())
                                .orFilters(
                                    List.of(
                                        f1 -> f1.state(ProcessInstanceState.ACTIVE),
                                        f1 -> f1.hasIncident(true))))
                    .sort(ProcessInstanceSort::startDate)
                    .page(p -> p.limit(100))),
        new ReadQuery(
            "process_instance_by_business_key",
            Duration.ofSeconds(30),
            (client, context) ->
                client
                    .newProcessInstanceSearchRequest()
                    .filter(
                        f ->
                            // search for the process instance started a minute ago
                            f.variables(
                                (Map<String, Object>)
                                    context.businessKeySupplier().get().apply(Map::of)))
                    .sort(ProcessInstanceSort::startDate)
                    .page(p -> p.limit(100))),
        new ReadQuery(
            "process_instance_by_key",
            Duration.ofSeconds(30),
            (client, context) -> client.newProcessInstanceGetRequest(context.processInstanceKey())),
        new ReadQuery(
            "process_definition_statistics",
            Duration.ofSeconds(30),
            (client, context) ->
                client.newProcessDefinitionInstanceStatisticsRequest().page(p -> p.limit(100))),
        new ReadQuery(
            "process_definition_element_statistics",
            Duration.ofSeconds(30),
            (client, context) ->
                client
                    .newProcessDefinitionElementStatisticsRequest(
                        context.benchmarkProcessDefinitionKey())
                    .filter(f -> f.state(s -> s.in(List.of(ProcessInstanceState.ACTIVE))))),
        new ReadQuery(
            "incident_by_error_statistics",
            Duration.ofSeconds(30),
            (client, context) ->
                client
                    .newIncidentProcessInstanceStatisticsByErrorRequest()
                    .page(p -> p.limit(100))
                    .sort(s -> s.activeInstancesWithErrorCount().desc())),
        new ReadQuery(
            "audit_log_by_process_instance_key",
            Duration.ofSeconds(30),
            (client, context) ->
                client
                    .newAuditLogSearchRequest()
                    .filter(f -> f.processInstanceKey(Long.toString(context.processInstanceKey())))
                    .page(p -> p.limit(100))
                    .sort(s -> s.timestamp().desc())),
        new ReadQuery(
            "audit_log_by_category",
            Duration.ofSeconds(30),
            (client, context) ->
                client
                    .newAuditLogSearchRequest()
                    .filter(f -> f.category(AuditLogCategoryEnum.DEPLOYED_RESOURCES))
                    .page(p -> p.limit(100))
                    .sort(s -> s.timestamp().desc())),
        new ReadQuery(
            "decision_instance_list",
            Duration.ofSeconds(30),
            (client, context) ->
                client
                    .newDecisionInstanceSearchRequest()
                    .filter(
                        f ->
                            f.state(
                                d ->
                                    d.in(
                                        List.of(
                                            DecisionInstanceState.EVALUATED,
                                            DecisionInstanceState.FAILED))))
                    .page(p -> p.limit(100))
                    .sort(s -> s.evaluationDate().desc())));
  }
}
