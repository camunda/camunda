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
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.STATE;
import static io.camunda.operate.store.opensearch.OpensearchIncidentStore.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.cardinalityAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.topHitsAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.webapp.reader.IncidentStatisticsReader;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentByProcessStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
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
  Aggregation COUNT_PROCESS_KEYS = termAggregation(PROCESS_KEY, TERMS_AGG_SIZE)._toAggregation();
  Query INCIDENTS_QUERY =
      and(
          term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
          term(STATE, ProcessInstanceState.ACTIVE.toString()),
          term(INCIDENT, true));

  @Autowired RichOpenSearchClient richOpenSearchClient;
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private ListViewTemplate processInstanceTemplate;
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

    Query query =
        permissionsService == null
            ? ACTIVE_INCIDENT_QUERY
            : and(
                ACTIVE_INCIDENT_QUERY,
                createQueryForProcessesByPermission(IdentityPermission.READ));

    var uniqueProcessInstances = cardinalityAggregation(IncidentTemplate.PROCESS_INSTANCE_KEY);
    var groupByProcessKeys =
        termAggregation(IncidentTemplate.PROCESS_DEFINITION_KEY, TERMS_AGG_SIZE);
    var errorMessage = topHitsAggregation(List.of(IncidentTemplate.ERROR_MSG), 1);
    var groupByErrorMessageHash = termAggregation(IncidentTemplate.ERROR_MSG_HASH, TERMS_AGG_SIZE);

    var searchRequestBuilder =
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

  private List<LongTermsBucket> searchAggBuckets(Query query) {
    var searchRequestBuilder =
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
      Map<Long, IncidentByProcessStatisticsDto> statistics) {
    Map<Long, IncidentByProcessStatisticsDto> results = new HashMap<>(statistics);
    Query query =
        withTenantCheck(
            and(
                term(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.toString()),
                term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION)));

    searchAggBuckets(query)
        .forEach(
            bucket -> {
              Long processDefinitionKey = Long.valueOf(bucket.key());
              long runningCount = bucket.docCount();
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
      Map<Long, IncidentByProcessStatisticsDto> incidentsByProcessMap) {
    Set<IncidentsByProcessGroupStatisticsDto> result =
        new TreeSet<>(IncidentsByProcessGroupStatisticsDto.COMPARATOR);

    final Map<ProcessStore.ProcessKey, List<ProcessEntity>> processGroups =
        processReader.getProcessesGrouped(new ProcessRequestDto());

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

  private Query createQueryForProcessesByPermission(IdentityPermission permission) {
    PermissionsService.ResourcesAllowed allowed =
        permissionsService.getProcessesWithPermission(permission);
    if (allowed == null) return null;
    return allowed.isAll()
        ? matchAll()
        : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
  }

  private IncidentsByErrorMsgStatisticsDto getIncidentsByErrorMsgStatistic(
      Map<Long, ProcessEntity> processes, LongTermsBucket errorMessageBucket) {
    record ErrorMessage(String errorMessage) {}

    ErrorMessage errorMessage =
        errorMessageBucket
            .aggregations()
            .get(ERROR_MESSAGE)
            .topHits()
            .hits()
            .hits()
            .get(0)
            .source()
            .to(ErrorMessage.class);

    IncidentsByErrorMsgStatisticsDto processStatistics =
        new IncidentsByErrorMsgStatisticsDto(errorMessage.errorMessage());

    errorMessageBucket
        .aggregations()
        .get(GROUP_BY_PROCESS_KEYS)
        .lterms()
        .buckets()
        .array()
        .forEach(
            bucket -> {
              Long processDefinitionKey = Long.valueOf(bucket.key());
              long incidentsCount =
                  bucket.aggregations().get(UNIQ_PROCESS_INSTANCES).cardinality().value();

              if (processes.containsKey(processDefinitionKey)) {
                IncidentByProcessStatisticsDto statisticForProcess =
                    new IncidentByProcessStatisticsDto(
                        processDefinitionKey.toString(),
                        errorMessage.errorMessage(),
                        incidentsCount);
                ProcessEntity process = processes.get(processDefinitionKey);
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
