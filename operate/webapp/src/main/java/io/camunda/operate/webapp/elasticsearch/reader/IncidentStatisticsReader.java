/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.store.elasticsearch.ElasticsearchIncidentStore.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.*;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentByProcessStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
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

  private static final Logger logger = LoggerFactory.getLogger(IncidentStatisticsReader.class);

  @Autowired private ListViewTemplate processInstanceTemplate;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private ProcessReader processReader;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  @Override
  public Set<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics() {
    final Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap =
        updateActiveInstances(getIncidentsByProcess());
    return collectStatisticsForProcessGroups(incidentsByProcessMap);
  }

  @Override
  public Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError() {
    Set<IncidentsByErrorMsgStatisticsDto> result =
        new TreeSet<>(IncidentsByErrorMsgStatisticsDto.COMPARATOR);

    Map<Long, ProcessEntity> processes =
        processReader.getProcessesWithFields(
            ProcessIndex.KEY,
            ProcessIndex.NAME,
            ProcessIndex.BPMN_PROCESS_ID,
            ProcessIndex.TENANT_ID,
            ProcessIndex.VERSION);

    TermsAggregationBuilder aggregation =
        terms(GROUP_BY_ERROR_MESSAGE_HASH)
            .field(IncidentTemplate.ERROR_MSG_HASH)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(
                topHits(ERROR_MESSAGE).size(1).fetchSource(IncidentTemplate.ERROR_MSG, null))
            .subAggregation(
                terms(GROUP_BY_PROCESS_KEYS)
                    .field(IncidentTemplate.PROCESS_DEFINITION_KEY)
                    .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                    .subAggregation(
                        cardinality(UNIQ_PROCESS_INSTANCES)
                            .field(IncidentTemplate.PROCESS_INSTANCE_KEY)));

    var query = ACTIVE_INCIDENT_QUERY;
    if (permissionsService != null) {
      query =
          joinWithAnd(
              ACTIVE_INCIDENT_QUERY, createQueryForProcessesByPermission(IdentityPermission.READ));
    }

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
            .source(new SearchSourceBuilder().query(query).aggregation(aggregation).size(0));

    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      Terms errorMessageAggregation =
          searchResponse.getAggregations().get(GROUP_BY_ERROR_MESSAGE_HASH);
      for (Bucket bucket : errorMessageAggregation.getBuckets()) {
        result.add(getIncidentsByErrorMsgStatistic(processes, bucket));
      }
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining incidents by error message: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
    return result;
  }

  private Map<Long, IncidentByProcessStatisticsDto> getIncidentsByProcess() {
    Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>();

    SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(processInstanceTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(INCIDENTS_QUERY)
                    .aggregation(COUNT_PROCESS_KEYS)
                    .size(0));

    try {
      SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      List<? extends Bucket> buckets =
          ((Terms) searchResponse.getAggregations().get(PROCESS_KEYS)).getBuckets();
      for (Bucket bucket : buckets) {
        Long processDefinitionKey = (Long) bucket.getKey();
        long incidents = bucket.getDocCount();
        results.put(
            processDefinitionKey,
            new IncidentByProcessStatisticsDto(processDefinitionKey.toString(), incidents, 0));
      }
      return results;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining incidents by process: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Map<Long, IncidentByProcessStatisticsDto> updateActiveInstances(
      Map<Long, IncidentByProcessStatisticsDto> statistics) {
    QueryBuilder runningInstanceQuery =
        joinWithAnd(
            termQuery(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.toString()),
            termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>(statistics);
    try {
      SearchRequest searchRequest =
          ElasticsearchUtil.createSearchRequest(processInstanceTemplate, ONLY_RUNTIME)
              .source(
                  new SearchSourceBuilder()
                      .query(runningInstanceQuery)
                      .aggregation(COUNT_PROCESS_KEYS)
                      .size(0));

      SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      List<? extends Bucket> buckets =
          ((Terms) searchResponse.getAggregations().get(PROCESS_KEYS)).getBuckets();
      for (Bucket bucket : buckets) {
        Long processDefinitionKey = (Long) bucket.getKey();
        long runningCount = bucket.getDocCount();
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
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining active processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Set<IncidentsByProcessGroupStatisticsDto> collectStatisticsForProcessGroups(
      Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap) {

    Set<IncidentsByProcessGroupStatisticsDto> result =
        new TreeSet<>(IncidentsByProcessGroupStatisticsDto.COMPARATOR);

    final var processGroups = processReader.getProcessesGrouped(new ProcessRequestDto());

    // iterate over process groups (bpmnProcessId)
    for (List<ProcessEntity> processes : processGroups.values()) {
      IncidentsByProcessGroupStatisticsDto stat = new IncidentsByProcessGroupStatisticsDto();
      stat.setBpmnProcessId(processes.get(0).getBpmnProcessId());
      stat.setTenantId(processes.get(0).getTenantId());

      // accumulate stat for process group
      long activeInstancesCount = 0;
      long instancesWithActiveIncidentsCount = 0;

      // max version to find out latest process name
      long maxVersion = 0;

      // iterate over process versions
      for (ProcessEntity processEntity : processes) {
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
  private QueryBuilder createQueryForProcessesByPermission(IdentityPermission permission) {
    PermissionsService.ResourcesAllowed allowed =
        permissionsService.getProcessesWithPermission(permission);
    if (allowed == null) return null;
    return allowed.isAll()
        ? QueryBuilders.matchAllQuery()
        : QueryBuilders.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }

  private IncidentsByErrorMsgStatisticsDto getIncidentsByErrorMsgStatistic(
      Map<Long, ProcessEntity> processes, Bucket errorMessageBucket) {
    SearchHits searchHits =
        ((TopHits) errorMessageBucket.getAggregations().get(ERROR_MESSAGE)).getHits();
    SearchHit searchHit = searchHits.getHits()[0];
    String errorMessage = (String) searchHit.getSourceAsMap().get(IncidentTemplate.ERROR_MSG);

    IncidentsByErrorMsgStatisticsDto processStatistics =
        new IncidentsByErrorMsgStatisticsDto(errorMessage);

    Terms processDefinitionKeyAggregation =
        (Terms) errorMessageBucket.getAggregations().get(GROUP_BY_PROCESS_KEYS);
    for (Bucket processDefinitionKeyBucket : processDefinitionKeyAggregation.getBuckets()) {
      Long processDefinitionKey = (Long) processDefinitionKeyBucket.getKey();
      long incidentsCount =
          ((Cardinality) processDefinitionKeyBucket.getAggregations().get(UNIQ_PROCESS_INSTANCES))
              .getValue();

      if (processes.containsKey(processDefinitionKey)) {
        IncidentByProcessStatisticsDto statisticForProcess =
            new IncidentByProcessStatisticsDto(
                processDefinitionKey.toString(), errorMessage, incidentsCount);
        ProcessEntity process = processes.get(processDefinitionKey);
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
