/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.OpensearchIncidentStore.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.cardinalityAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.topHitsAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.STATE;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.reader.IncidentStatisticsReader;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentByProcessStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchIncidentStatisticsReader implements IncidentStatisticsReader {
  private static final String ERROR_MESSAGE = "errorMessages";
  private static final String GROUP_BY_ERROR_MESSAGE_HASH = "group_by_errorMessages";
  private static final String GROUP_BY_PROCESS_KEYS = "group_by_processDefinitionKeys";
  private static final String UNIQ_PROCESS_INSTANCES = "uniq_processInstances";
  private static final Aggregation COUNT_PROCESS_KEYS =
      termAggregation(PROCESS_KEY, TERMS_AGG_SIZE)._toAggregation();
  private static final Query INCIDENTS_QUERY =
      and(
          term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
          term(STATE, ProcessInstanceState.ACTIVE.toString()),
          term(INCIDENT, true));

  @Autowired RichOpenSearchClient richOpenSearchClient;
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private ListViewTemplate processInstanceTemplate;
  @Autowired private ProcessReader processReader;
  @Autowired private PermissionsService permissionsService;

  @Override
  public Set<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics() {
    final Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap =
        updateActiveInstances(getIncidentsByProcess());
    return collectStatisticsForProcessGroups(incidentsByProcessMap);
  }

  @Override
  public Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError() {
    final Set<IncidentsByErrorMsgStatisticsDto> result =
        new TreeSet<>(IncidentsByErrorMsgStatisticsDto.COMPARATOR);

    final Map<Long, ProcessEntity> processes =
        processReader.getProcessesWithFields(
            ProcessIndex.KEY,
            ProcessIndex.NAME,
            ProcessIndex.BPMN_PROCESS_ID,
            ProcessIndex.TENANT_ID,
            ProcessIndex.VERSION);

    final Query query =
        and(
            ACTIVE_INCIDENT_QUERY,
            createQueryForProcessesByPermission(PermissionType.READ_PROCESS_INSTANCE));

    final var uniqueProcessInstances =
        cardinalityAggregation(IncidentTemplate.PROCESS_INSTANCE_KEY);
    final var groupByProcessKeys =
        termAggregation(IncidentTemplate.PROCESS_DEFINITION_KEY, TERMS_AGG_SIZE);
    final var errorMessage =
        topHitsAggregation(List.of(IncidentTemplate.ERROR_MSG, IncidentTemplate.ERROR_MSG_HASH), 1);
    final var groupByErrorMessageHash =
        termAggregation(IncidentTemplate.ERROR_MSG_HASH, TERMS_AGG_SIZE);

    final var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(withTenantCheck(query))
            .aggregations(
                GROUP_BY_ERROR_MESSAGE_HASH,
                withSubaggregations(
                    groupByErrorMessageHash,
                    Map.of(
                        ERROR_MESSAGE, errorMessage._toAggregation(),
                        GROUP_BY_PROCESS_KEYS,
                            withSubaggregations(
                                groupByProcessKeys,
                                Map.of(
                                    UNIQ_PROCESS_INSTANCES,
                                    uniqueProcessInstances._toAggregation())))));

    richOpenSearchClient
        .doc()
        .searchAggregations(searchRequestBuilder)
        .get(GROUP_BY_ERROR_MESSAGE_HASH)
        .lterms()
        .buckets()
        .array()
        .forEach(bucket -> result.add(getIncidentsByErrorMsgStatistic(processes, bucket)));

    return result;
  }

  private List<LongTermsBucket> searchAggBuckets(final Query query) {
    final var searchRequestBuilder =
        searchRequestBuilder(processInstanceTemplate, ONLY_RUNTIME)
            .query(withTenantCheck(query))
            .aggregations(PROCESS_KEYS, COUNT_PROCESS_KEYS);

    return richOpenSearchClient
        .doc()
        .searchAggregations(searchRequestBuilder)
        .get(PROCESS_KEYS)
        .lterms()
        .buckets()
        .array();
  }

  private Map<Long, IncidentByProcessStatisticsDto> getIncidentsByProcess() {
    return searchAggBuckets(withTenantCheck(INCIDENTS_QUERY)).stream()
        .collect(
            Collectors.toMap(
                bucket -> Long.valueOf(bucket.key()),
                bucket -> new IncidentByProcessStatisticsDto(bucket.key(), bucket.docCount(), 0)));
  }

  private Map<Long, IncidentByProcessStatisticsDto> updateActiveInstances(
      final Map<Long, IncidentByProcessStatisticsDto> statistics) {
    final Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>(statistics);
    final Query query =
        withTenantCheck(
            and(
                term(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.toString()),
                term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION)));

    searchAggBuckets(query)
        .forEach(
            bucket -> {
              final Long processDefinitionKey = Long.valueOf(bucket.key());
              final long runningCount = bucket.docCount();
              IncidentByProcessStatisticsDto statistic = results.get(processDefinitionKey);
              if (statistic != null) {
                statistic.setActiveInstancesCount(
                    runningCount - statistic.getInstancesWithActiveIncidentsCount());
              } else {
                statistic = new IncidentByProcessStatisticsDto(bucket.key(), 0, runningCount);
              }
              results.put(processDefinitionKey, statistic);
            });

    return results;
  }

  private Set<IncidentsByProcessGroupStatisticsDto> collectStatisticsForProcessGroups(
      final Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap) {
    final Set<IncidentsByProcessGroupStatisticsDto> result =
        new TreeSet<>(IncidentsByProcessGroupStatisticsDto.COMPARATOR);

    final Map<ProcessStore.ProcessKey, List<ProcessEntity>> processGroups =
        processReader.getProcessesGrouped(new ProcessRequestDto());

    // iterate over process groups (bpmnProcessId)
    for (final List<ProcessEntity> processes : processGroups.values()) {
      final IncidentsByProcessGroupStatisticsDto stat = new IncidentsByProcessGroupStatisticsDto();
      stat.setBpmnProcessId(processes.get(0).getBpmnProcessId());
      stat.setTenantId(processes.get(0).getTenantId());

      // accumulate stat for process group
      long activeInstancesCount = 0;
      long instancesWithActiveIncidentsCount = 0;

      // max version to find out latest process name
      long maxVersion = 0;

      // iterate over process versions
      for (final ProcessEntity processEntity : processes) {
        IncidentByProcessStatisticsDto statForProcess =
            incidentsByProcessMap.get(processEntity.getKey());
        if (statForProcess != null) {
          activeInstancesCount += statForProcess.getActiveInstancesCount();
          instancesWithActiveIncidentsCount +=
              statForProcess.getInstancesWithActiveIncidentsCount();
        } else {
          statForProcess =
              new IncidentByProcessStatisticsDto(
                  ConversionUtils.toStringOrNull(processEntity.getKey()), 0, 0);
        }
        statForProcess.setName(processEntity.getName());
        statForProcess.setBpmnProcessId(processEntity.getBpmnProcessId());
        statForProcess.setTenantId(processEntity.getTenantId());
        statForProcess.setVersion(processEntity.getVersion());
        stat.getProcesses().add(statForProcess);

        // set the latest name
        if (processEntity.getVersion() > maxVersion) {
          stat.setProcessName(processEntity.getName());
          maxVersion = processEntity.getVersion();
        }
      }

      stat.setActiveInstancesCount(activeInstancesCount);
      stat.setInstancesWithActiveIncidentsCount(instancesWithActiveIncidentsCount);
      result.add(stat);
    }
    return result;
  }

  private Query createQueryForProcessesByPermission(final PermissionType permission) {
    final PermissionsService.ResourcesAllowed allowed =
        permissionsService.getProcessesWithPermission(permission);
    return allowed.isAll()
        ? matchAll()
        : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }

  private IncidentsByErrorMsgStatisticsDto getIncidentsByErrorMsgStatistic(
      final Map<Long, ProcessEntity> processes, final LongTermsBucket errorMessageBucket) {
    record ErrorMessage(String errorMessage, Integer errorMessageHash) {}

    final ErrorMessage errorMessage =
        errorMessageBucket
            .aggregations()
            .get(ERROR_MESSAGE)
            .topHits()
            .hits()
            .hits()
            .get(0)
            .source()
            .to(ErrorMessage.class);

    final IncidentsByErrorMsgStatisticsDto processStatistics =
        new IncidentsByErrorMsgStatisticsDto(
            errorMessage.errorMessage(), errorMessage.errorMessageHash());

    errorMessageBucket
        .aggregations()
        .get(GROUP_BY_PROCESS_KEYS)
        .lterms()
        .buckets()
        .array()
        .forEach(
            bucket -> {
              final Long processDefinitionKey = Long.valueOf(bucket.key());
              final long incidentsCount =
                  bucket.aggregations().get(UNIQ_PROCESS_INSTANCES).cardinality().value();

              if (processes.containsKey(processDefinitionKey)) {
                final IncidentByProcessStatisticsDto statisticForProcess =
                    new IncidentByProcessStatisticsDto(
                        processDefinitionKey.toString(),
                        errorMessage.errorMessage(),
                        incidentsCount);
                final ProcessEntity process = processes.get(processDefinitionKey);
                statisticForProcess.setName(process.getName());
                statisticForProcess.setBpmnProcessId(process.getBpmnProcessId());
                statisticForProcess.setTenantId(process.getTenantId());
                statisticForProcess.setVersion(process.getVersion());
                processStatistics.getProcesses().add(statisticForProcess);
              }
              processStatistics.recordInstancesCount(incidentsCount);
            });

    return processStatistics;
  }
}
