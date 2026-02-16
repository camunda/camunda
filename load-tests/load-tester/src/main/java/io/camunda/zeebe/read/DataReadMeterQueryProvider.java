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
import io.camunda.zeebe.config.StarterCfg;
import io.camunda.zeebe.read.DataReadMeter.ReadQuery;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class DataReadMeterQueryProvider {

  private DataReadMeterQueryProvider() {}

  public static List<ReadQuery> getDefaultQueries(
      final StarterCfg starterCfg,
      final AtomicLong businessKey,
      final Long benchmarkProcessDefinitionKey,
      final AtomicLong lastProcessInstanceKey) {
    return List.of(
        new ReadQuery(
            "process_instances_active",
            Duration.ofSeconds(30),
            c ->
                c.newProcessInstanceSearchRequest()
                    .filter(
                        f ->
                            f.processDefinitionId(starterCfg.getProcessId())
                                .orFilters(
                                    List.of(
                                        f1 -> f1.state(ProcessInstanceState.ACTIVE),
                                        f1 -> f1.hasIncident(true))))
                    .sort(ProcessInstanceSort::startDate)
                    .page(p -> p.limit(100))),
        new ReadQuery(
            "process_instance_by_business_key",
            Duration.ofSeconds(30),
            c ->
                c.newProcessInstanceSearchRequest()
                    .filter(
                        f ->
                            // search for the process instance started a minute ago
                            f.variables(
                                Map.of(
                                    starterCfg.getBusinessKey(),
                                    businessKey.get() - starterCfg.getRate() * 60L)))
                    .sort(ProcessInstanceSort::startDate)
                    .page(p -> p.limit(100))),
        new ReadQuery(
            "process_instance_by_key",
            Duration.ofSeconds(30),
            c -> c.newProcessInstanceGetRequest(lastProcessInstanceKey.get())),
        new ReadQuery(
            "process_definition_statistics",
            Duration.ofSeconds(30),
            c -> c.newProcessDefinitionInstanceStatisticsRequest().page(p -> p.limit(100))),
        new ReadQuery(
            "process_definition_element_statistics",
            Duration.ofSeconds(30),
            c ->
                c.newProcessDefinitionElementStatisticsRequest(benchmarkProcessDefinitionKey)
                    .filter(f -> f.state(s -> s.in(List.of(ProcessInstanceState.ACTIVE))))),
        new ReadQuery(
            "incident_by_error_statistics",
            Duration.ofSeconds(30),
            c ->
                c.newIncidentProcessInstanceStatisticsByErrorRequest()
                    .page(p -> p.limit(100))
                    .sort(s -> s.activeInstancesWithErrorCount().desc())),
        new ReadQuery(
            "audit_log_by_process_instance_key",
            Duration.ofSeconds(30),
            c ->
                c.newAuditLogSearchRequest()
                    .filter(f -> f.processInstanceKey(Long.toString(lastProcessInstanceKey.get())))
                    .page(p -> p.limit(100))
                    .sort(s -> s.timestamp().desc())),
        new ReadQuery(
            "audit_log_by_category",
            Duration.ofSeconds(30),
            c ->
                c.newAuditLogSearchRequest()
                    .filter(f -> f.category(AuditLogCategoryEnum.DEPLOYED_RESOURCES))
                    .page(p -> p.limit(100))
                    .sort(s -> s.timestamp().desc())),
        new ReadQuery(
            "decision_instance_list",
            Duration.ofSeconds(30),
            c ->
                c.newDecisionInstanceSearchRequest()
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
