/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.store.elasticsearch.ElasticsearchIncidentStore.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.*;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.ElasticsearchUtil;
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
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
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

  @Autowired private ListViewTemplate processInstanceTemplate;

  @Autowired private IncidentTemplate incidentTemplate;

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

    final TermsAggregationBuilder aggregation =
        terms(GROUP_BY_ERROR_MESSAGE_HASH)
            .field(IncidentTemplate.ERROR_MSG_HASH)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(
                topHits(ERROR_MESSAGE)
                    .size(1)
                    .fetchSource(
                        new String[] {IncidentTemplate.ERROR_MSG, IncidentTemplate.ERROR_MSG_HASH},
                        null))
            .subAggregation(
                terms(GROUP_BY_PROCESS_KEYS)
                    .field(IncidentTemplate.PROCESS_DEFINITION_KEY)
                    .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                    .subAggregation(
                        cardinality(UNIQ_PROCESS_INSTANCES)
                            .field(IncidentTemplate.PROCESS_INSTANCE_KEY)));

    final var query =
        joinWithAnd(
            ACTIVE_INCIDENT_QUERY,
            createQueryForProcessesByPermission(PermissionType.READ_PROCESS_INSTANCE));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(new SearchSourceBuilder().query(query).aggregation(aggregation).size(0));

    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      final Terms errorMessageAggregation =
          searchResponse.getAggregations().get(GROUP_BY_ERROR_MESSAGE_HASH);
      for (final Bucket bucket : errorMessageAggregation.getBuckets()) {
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

  private Map<Long, IncidentByProcessStatisticsDto> getIncidentsByProcess() {
    final Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>();

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(processInstanceTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(INCIDENTS_QUERY)
                    .aggregation(COUNT_PROCESS_KEYS)
                    .size(0));

    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      final List<? extends Bucket> buckets =
          ((Terms) searchResponse.getAggregations().get(PROCESS_KEYS)).getBuckets();
      for (final Bucket bucket : buckets) {
        final Long processDefinitionKey = (Long) bucket.getKey();
        final long incidents = bucket.getDocCount();
        results.put(
            processDefinitionKey,
            new IncidentByProcessStatisticsDto(processDefinitionKey.toString(), incidents, 0));
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
      final Map<Long, IncidentByProcessStatisticsDto> statistics) {
    final QueryBuilder runningInstanceQuery =
        joinWithAnd(
            termQuery(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.toString()),
            termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    final Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>(statistics);
    try {
      final SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(processInstanceTemplate, ONLY_RUNTIME)
              .source(
                  new SearchSourceBuilder()
                      .query(runningInstanceQuery)
                      .aggregation(COUNT_PROCESS_KEYS)
                      .size(0));

      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      final List<? extends Bucket> buckets =
          ((Terms) searchResponse.getAggregations().get(PROCESS_KEYS)).getBuckets();
      for (final Bucket bucket : buckets) {
        final Long processDefinitionKey = (Long) bucket.getKey();
        final long runningCount = bucket.getDocCount();
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
  private QueryBuilder createQueryForProcessesByPermission(final PermissionType permission) {
    final PermissionsService.ResourcesAllowed allowed =
        permissionsService.getProcessesWithPermission(permission);
    return allowed.isAll()
        ? QueryBuilders.matchAllQuery()
        : QueryBuilders.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }

  private IncidentsByErrorMsgStatisticsDto getIncidentsByErrorMsgStatistic(
      final Map<Long, ProcessEntity> processes, final Bucket errorMessageBucket) {
    final SearchHits searchHits =
        ((TopHits) errorMessageBucket.getAggregations().get(ERROR_MESSAGE)).getHits();
    final SearchHit searchHit = searchHits.getHits()[0];
    final String errorMessage = (String) searchHit.getSourceAsMap().get(IncidentTemplate.ERROR_MSG);
    final Integer errorMessageHashCode =
        (Integer) searchHit.getSourceAsMap().get(IncidentTemplate.ERROR_MSG_HASH);
    final IncidentsByErrorMsgStatisticsDto processStatistics =
        new IncidentsByErrorMsgStatisticsDto(errorMessage, errorMessageHashCode);

    final Terms processDefinitionKeyAggregation =
        (Terms) errorMessageBucket.getAggregations().get(GROUP_BY_PROCESS_KEYS);
    for (final Bucket processDefinitionKeyBucket : processDefinitionKeyAggregation.getBuckets()) {
      final Long processDefinitionKey = (Long) processDefinitionKeyBucket.getKey();
      final long incidentsCount =
          ((Cardinality) processDefinitionKeyBucket.getAggregations().get(UNIQ_PROCESS_INSTANCES))
              .getValue();

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
