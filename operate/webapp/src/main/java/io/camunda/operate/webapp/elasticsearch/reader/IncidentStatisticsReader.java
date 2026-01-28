/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.store.elasticsearch.ElasticsearchIncidentStore.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.util.ElasticsearchUtil.MAP_CLASS;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class IncidentStatisticsReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.IncidentStatisticsReader {

  private static final String ERROR_MESSAGE = "errorMessages";
  private static final String UNIQ_PROCESS_INSTANCES = "uniq_processInstances";
  private static final String GROUP_BY_ERROR_MESSAGE_HASH = "group_by_errorMessages";
  private static final String GROUP_BY_PROCESS_KEYS = "group_by_processDefinitionKeys";

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentStatisticsReader.class);

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private ProcessReader processReader;

  @Autowired private PermissionsService permissionsService;

  @Override
  public Set<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics() {
    final Query pisWithReadPermissionQuery = createQueryForProcessInstancesWithReadPermission();
    final Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap =
        updateActiveInstances(
            getIncidentsByProcess(pisWithReadPermissionQuery), pisWithReadPermissionQuery);
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

    final var uniqueProcessInstances =
        new Aggregation.Builder()
            .cardinality(c -> c.field(IncidentTemplate.PROCESS_INSTANCE_KEY))
            .build();

    final var groupByProcessKeys =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.field(IncidentTemplate.PROCESS_DEFINITION_KEY)
                        .size(ElasticsearchUtil.TERMS_AGG_SIZE))
            .aggregations(Map.of(UNIQ_PROCESS_INSTANCES, uniqueProcessInstances))
            .build();

    final var errorMessage =
        new Aggregation.Builder()
            .topHits(
                th ->
                    th.size(1)
                        .source(
                            s ->
                                s.filter(
                                    f ->
                                        f.includes(
                                            IncidentTemplate.ERROR_MSG,
                                            IncidentTemplate.ERROR_MSG_HASH))))
            .build();

    final var groupByErrorMessageHash =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.field(IncidentTemplate.ERROR_MSG_HASH).size(ElasticsearchUtil.TERMS_AGG_SIZE))
            .aggregations(
                Map.of(
                    ERROR_MESSAGE, errorMessage,
                    GROUP_BY_PROCESS_KEYS, groupByProcessKeys))
            .build();

    final var query =
        joinWithAnd(ACTIVE_INCIDENT_QUERY, createQueryForProcessInstancesWithReadPermission());
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequest =
        new SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(incidentTemplate, QueryType.ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .aggregations(GROUP_BY_ERROR_MESSAGE_HASH, groupByErrorMessageHash)
            .size(0)
            .build();

    try {
      final var searchResponse = esClient.search(searchRequest, MAP_CLASS);

      final var errorMessageAggregation =
          searchResponse.aggregations().get(GROUP_BY_ERROR_MESSAGE_HASH).lterms();

      for (final var bucket : errorMessageAggregation.buckets().array()) {
        result.add(getIncidentsByErrorMsgStatistic(processes, bucket));
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining incidents by error message: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
    return result;
  }

  private Map<Long, IncidentByProcessStatisticsDto> getIncidentsByProcess(
      final Query permittedProcessInstancesQuery) {
    final Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>();

    final var incidentsQuery =
        joinWithAnd(
            ElasticsearchUtil.termsQuery(
                ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION),
            ElasticsearchUtil.termsQuery(
                ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.toString()),
            ElasticsearchUtil.termsQuery(ListViewTemplate.INCIDENT, true),
            permittedProcessInstancesQuery);

    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(incidentsQuery);

    final var searchRequest =
        new SearchRequest.Builder()
            .index(listViewTemplate.getFullQualifiedName())
            .query(tenantAwareQuery)
            .aggregations(PROCESS_KEYS, COUNT_PROCESS_KEYS)
            .size(0)
            .build();

    try {
      final var searchResponse = esClient.search(searchRequest, Void.class);

      final var buckets =
          searchResponse.aggregations().get(PROCESS_KEYS).lterms().buckets().array();

      for (final var bucket : buckets) {
        final Long processDefinitionKey = bucket.key();
        final long incidentCount = bucket.docCount();
        results.put(
            processDefinitionKey,
            new IncidentByProcessStatisticsDto(processDefinitionKey.toString(), incidentCount, 0));
      }
      return results;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining incidents by process: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Map<Long, IncidentByProcessStatisticsDto> updateActiveInstances(
      final Map<Long, IncidentByProcessStatisticsDto> statistics,
      final Query processInstancePermissionQuery) {
    final Query runningInstanceQuery =
        joinWithAnd(
            ElasticsearchUtil.termsQuery(
                ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.toString()),
            ElasticsearchUtil.termsQuery(
                ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION),
            processInstancePermissionQuery);

    final Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>(statistics);

    try {
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(runningInstanceQuery);
      final var searchRequest =
          new SearchRequest.Builder()
              .index(listViewTemplate.getFullQualifiedName())
              .query(tenantAwareQuery)
              .aggregations(PROCESS_KEYS, COUNT_PROCESS_KEYS)
              .size(0)
              .build();

      final var searchResponse = esClient.search(searchRequest, Void.class);

      final var buckets =
          searchResponse.aggregations().get(PROCESS_KEYS).lterms().buckets().array();

      for (final var bucket : buckets) {
        final Long processDefinitionKey = bucket.key();
        final long runningCount = bucket.docCount();
        IncidentByProcessStatisticsDto statistic = results.get(processDefinitionKey);
        if (statistic != null) {
          statistic.setActiveInstancesCount(
              runningCount - statistic.getInstancesWithActiveIncidentsCount());
        } else {
          statistic =
              new IncidentByProcessStatisticsDto(processDefinitionKey.toString(), 0, runningCount);
        }
        results.put(processDefinitionKey, statistic);
      }
      return results;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining active processes: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Set<IncidentsByProcessGroupStatisticsDto> collectStatisticsForProcessGroups(
      final Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap) {

    final Set<IncidentsByProcessGroupStatisticsDto> result =
        new TreeSet<>(IncidentsByProcessGroupStatisticsDto.COMPARATOR);

    final var processGroups = processReader.getProcessesGrouped(new ProcessRequestDto());

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

  /**
   * createQueryForProcessesByPermission
   *
   * @return query that matches the processes for which the user has the given permission
   */
  private Query createQueryForProcessInstancesWithReadPermission() {
    final PermissionsService.ResourcesAllowed allowed =
        permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE);
    return allowed.isAll()
        ? ElasticsearchUtil.matchAllQuery()
        : ElasticsearchUtil.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }

  private IncidentsByErrorMsgStatisticsDto getIncidentsByErrorMsgStatistic(
      final Map<Long, ProcessEntity> processes, final LongTermsBucket errorMessageBucket) {
    final var searchHits =
        errorMessageBucket.aggregations().get(ERROR_MESSAGE).topHits().hits().hits();

    if (searchHits.isEmpty()) {
      throw new OperateRuntimeException(
          "Could not find error messages in aggregation: " + errorMessageBucket.keyAsString());
    }

    final Map<String, Object> sourceMap = searchHits.get(0).source().to(MAP_CLASS);
    final String errorMessage = (String) sourceMap.get(IncidentTemplate.ERROR_MSG);
    final Integer errorMessageHashCode = (Integer) sourceMap.get(IncidentTemplate.ERROR_MSG_HASH);

    final IncidentsByErrorMsgStatisticsDto processStatistics =
        new IncidentsByErrorMsgStatisticsDto(errorMessage, errorMessageHashCode);

    final var processDefinitionKeyAggregation =
        errorMessageBucket.aggregations().get(GROUP_BY_PROCESS_KEYS).lterms();

    for (final var processDefinitionKeyBucket : processDefinitionKeyAggregation.buckets().array()) {
      final Long processDefinitionKey = processDefinitionKeyBucket.key();
      final long incidentsCount =
          processDefinitionKeyBucket
              .aggregations()
              .get(UNIQ_PROCESS_INSTANCES)
              .cardinality()
              .value();

      if (processes.containsKey(processDefinitionKey)) {
        final IncidentByProcessStatisticsDto statisticForProcess =
            new IncidentByProcessStatisticsDto(
                processDefinitionKey.toString(), errorMessage, incidentsCount);
        final ProcessEntity process = processes.get(processDefinitionKey);
        statisticForProcess.setName(process.getName());
        statisticForProcess.setBpmnProcessId(process.getBpmnProcessId());
        statisticForProcess.setTenantId(process.getTenantId());
        statisticForProcess.setVersion(process.getVersion());
        processStatistics.getProcesses().add(statisticForProcess);
      }
      processStatistics.recordInstancesCount(incidentsCount);
    }
    return processStatistics;
  }
}
