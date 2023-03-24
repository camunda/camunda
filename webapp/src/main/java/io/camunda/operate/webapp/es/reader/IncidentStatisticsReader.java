/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.schema.templates.IncidentTemplate.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentByProcessStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.elasticsearch.search.aggregations.AggregationBuilders.*;

@Component
public class IncidentStatisticsReader extends AbstractReader {

  private static final String ERROR_MESSAGE = "errorMessages";

  public static final String PROCESS_KEYS = "processDefinitionKeys";

  private static final String UNIQ_PROCESS_INSTANCES = "uniq_processInstances";

  private static final String GROUP_BY_ERROR_MESSAGE_HASH = "group_by_errorMessages";

  private static final String GROUP_BY_PROCESS_KEYS = "group_by_processDefinitionKeys";

  private static final Logger logger = LoggerFactory.getLogger(IncidentStatisticsReader.class);

  @Autowired
  private ListViewTemplate processInstanceTemplate;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private ProcessReader processReader;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  public static final AggregationBuilder COUNT_PROCESS_KEYS = terms(PROCESS_KEYS)
                                                        .field(ListViewTemplate.PROCESS_KEY)
                                                        .size(ElasticsearchUtil.TERMS_AGG_SIZE);

  public static final QueryBuilder INCIDENTS_QUERY =
      joinWithAnd(
          termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
          termQuery(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.toString()),
          termQuery(INCIDENT, true));


  public Set<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics(){
    final Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap = updateActiveInstances(getIncidentsByProcess());
    return collectStatisticsForProcessGroups(incidentsByProcessMap);
  }

  private Map<Long, IncidentByProcessStatisticsDto> getIncidentsByProcess() {
    Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>();


    SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(processInstanceTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(INCIDENTS_QUERY)
            .aggregation(COUNT_PROCESS_KEYS).size(0));

    try {
      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get(PROCESS_KEYS)).getBuckets();
      for (Bucket bucket : buckets) {
        Long processDefinitionKey = (Long) bucket.getKey();
        long incidents = bucket.getDocCount();
        results.put(processDefinitionKey, new IncidentByProcessStatisticsDto(processDefinitionKey.toString(),incidents, 0));
      }
      return results;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents by process: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Map<Long, IncidentByProcessStatisticsDto> updateActiveInstances(Map<Long,IncidentByProcessStatisticsDto> statistics) {
    QueryBuilder runningInstanceQuery = joinWithAnd(
        termQuery(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.toString()),
        termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>(statistics);
    try {
      SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(processInstanceTemplate, ONLY_RUNTIME)
          .source(new SearchSourceBuilder()
              .query(runningInstanceQuery)
              .aggregation(COUNT_PROCESS_KEYS)
              .size(0));

      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<? extends Bucket> buckets = ((Terms) searchResponse.getAggregations().get(PROCESS_KEYS)).getBuckets();
      for (Bucket bucket : buckets) {
        Long processDefinitionKey = (Long)bucket.getKey();
        long runningCount = bucket.getDocCount();
        IncidentByProcessStatisticsDto statistic = results.get(processDefinitionKey);
        if (statistic != null) {
          statistic.setActiveInstancesCount(runningCount - statistic.getInstancesWithActiveIncidentsCount());
        } else {
          statistic = new IncidentByProcessStatisticsDto(processDefinitionKey.toString(), 0, runningCount);
        }
        results.put(processDefinitionKey, statistic);
      }
      return results;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining active processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Set<IncidentsByProcessGroupStatisticsDto> collectStatisticsForProcessGroups(Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap) {

    Set<IncidentsByProcessGroupStatisticsDto> result = new TreeSet<>(IncidentsByProcessGroupStatisticsDto.COMPARATOR);

    final Map<String, List<ProcessEntity>> processGroups = processReader.getProcessesGrouped();

    //iterate over process groups (bpmnProcessId)
    for (Map.Entry<String, List<ProcessEntity>> entry: processGroups.entrySet()) {
      IncidentsByProcessGroupStatisticsDto stat = new IncidentsByProcessGroupStatisticsDto();
      stat.setBpmnProcessId(entry.getKey());

      //accumulate stat for process group
      long activeInstancesCount = 0;
      long instancesWithActiveIncidentsCount = 0;

      //max version to find out latest process name
      long maxVersion = 0;

      //iterate over process versions
      for (ProcessEntity processEntity: entry.getValue()) {
        IncidentByProcessStatisticsDto statForProcess = incidentsByProcessMap.get(processEntity.getKey());
        if (statForProcess != null) {
          activeInstancesCount += statForProcess.getActiveInstancesCount();
          instancesWithActiveIncidentsCount += statForProcess.getInstancesWithActiveIncidentsCount();
        }else {
          statForProcess = new IncidentByProcessStatisticsDto(ConversionUtils.toStringOrNull(processEntity.getKey()),0,0);
        }
        statForProcess.setName(processEntity.getName());
        statForProcess.setBpmnProcessId(processEntity.getBpmnProcessId());
        statForProcess.setVersion(processEntity.getVersion());
        stat.getProcesses().add(statForProcess);

        //set the latest name
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

  public Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError(){
    Set<IncidentsByErrorMsgStatisticsDto> result = new TreeSet<>(IncidentsByErrorMsgStatisticsDto.COMPARATOR);

    Map<Long, ProcessEntity> processes = processReader.getProcessesWithFields(
        ProcessIndex.KEY, ProcessIndex.NAME, ProcessIndex.BPMN_PROCESS_ID, ProcessIndex.VERSION);

    TermsAggregationBuilder aggregation = terms(GROUP_BY_ERROR_MESSAGE_HASH)
        .field(IncidentTemplate.ERROR_MSG_HASH)
        .size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .subAggregation(topHits(ERROR_MESSAGE).size(1).fetchSource(IncidentTemplate.ERROR_MSG,null))
        .subAggregation(terms(GROUP_BY_PROCESS_KEYS)
            .field(IncidentTemplate.PROCESS_DEFINITION_KEY)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(cardinality(UNIQ_PROCESS_INSTANCES)
                .field(IncidentTemplate.PROCESS_INSTANCE_KEY)));

    QueryBuilder query = (permissionsService == null) ? ACTIVE_INCIDENT_QUERY :
        joinWithAnd(ACTIVE_INCIDENT_QUERY, permissionsService.createQueryForProcessesByPermission(IdentityPermission.READ));
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(query)
            .aggregation(aggregation)
            .size(0));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      Terms errorMessageAggregation = searchResponse.getAggregations().get(GROUP_BY_ERROR_MESSAGE_HASH);
      for (Bucket bucket : errorMessageAggregation.getBuckets()) {
        result.add(getIncidentsByErrorMsgStatistic(processes, bucket));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents by error message: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
    return result;
  }

  private IncidentsByErrorMsgStatisticsDto getIncidentsByErrorMsgStatistic(Map<Long, ProcessEntity> processes, Bucket errorMessageBucket) {
    SearchHits searchHits = ((TopHits)errorMessageBucket.getAggregations().get(ERROR_MESSAGE)).getHits();
    SearchHit searchHit = searchHits.getHits()[0];
    String errorMessage  = (String) searchHit.getSourceAsMap().get(IncidentTemplate.ERROR_MSG);

    IncidentsByErrorMsgStatisticsDto processStatistics = new IncidentsByErrorMsgStatisticsDto(errorMessage);

    Terms processDefinitionKeyAggregation = (Terms) errorMessageBucket.getAggregations().get(GROUP_BY_PROCESS_KEYS);
    for (Bucket processDefinitionKeyBucket : processDefinitionKeyAggregation.getBuckets()) {
      Long processDefinitionKey = (Long)processDefinitionKeyBucket.getKey();
      long incidentsCount = ((Cardinality)processDefinitionKeyBucket.getAggregations().get(UNIQ_PROCESS_INSTANCES)).getValue();

      if (processes.containsKey(processDefinitionKey)) {
        IncidentByProcessStatisticsDto statisticForProcess = new IncidentByProcessStatisticsDto(processDefinitionKey.toString(), errorMessage, incidentsCount);
        ProcessEntity process = processes.get(processDefinitionKey);
        statisticForProcess.setName(process.getName());
        statisticForProcess.setBpmnProcessId(process.getBpmnProcessId());
        statisticForProcess.setVersion(process.getVersion());
        processStatistics.getProcesses().add(statisticForProcess);
      }
      processStatistics.recordInstancesCount(incidentsCount);
    }
    return processStatistics;
  }
}
