/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_NAME;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceCoreStatisticsDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.zeebeimport.util.TreePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(ProcessInstanceReader.class);

  public static final FilterAggregationBuilder INCIDENTS_AGGREGATION = AggregationBuilders.filter(
      "incidents",
      joinWithAnd(
          termQuery(INCIDENT, true),
          termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION)
      )
  );

  public static final FilterAggregationBuilder RUNNING_AGGREGATION = AggregationBuilders.filter(
      "running",
      termQuery(
          ListViewTemplate.STATE,
          ProcessInstanceState.ACTIVE
       )
  );

  @Autowired
  private ListViewTemplate listViewTemplate;

   @Autowired
  private OperationReader operationReader;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  /**
   * Searches for process instance by key.
   * @param processInstanceKey
   * @return
   */
  public ListViewProcessInstanceDto getProcessInstanceWithOperationsByKey(Long processInstanceKey) {
    try {
      final ProcessInstanceForListViewEntity processInstance = searchProcessInstanceByKey(processInstanceKey);

      final List<ProcessInstanceReferenceDto> callHierarchy = createCallHierarchy(
          processInstance.getTreePath(), String.valueOf(processInstanceKey));

      return ListViewProcessInstanceDto.createFrom(processInstance,
            operationReader.getOperationsByProcessInstanceKey(processInstanceKey),
            callHierarchy,
            permissionsService,
            objectMapper
      );
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance with operations: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private List<ProcessInstanceReferenceDto> createCallHierarchy(final String treePath,
      final String currentProcessInstanceId) {
    final List<ProcessInstanceReferenceDto> callHierarchy = new ArrayList<>();
    final List<String> processInstanceIds = new TreePath(treePath).extractProcessInstanceIds();
    //remove id of current process instance
    processInstanceIds.remove(currentProcessInstanceId);
    final QueryBuilder query = joinWithAnd(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        termsQuery(ID, processInstanceIds));
    final SearchRequest request = ElasticsearchUtil.createSearchRequest(listViewTemplate)
        .source(new SearchSourceBuilder().query(query)
            .fetchSource(new String[]{ID, PROCESS_KEY, PROCESS_NAME, BPMN_PROCESS_ID}, null));
    try {
      scrollWith(request, esClient, searchHits -> {
        Arrays.stream(searchHits.getHits())
            .forEach(sh -> {
              final Map<String, Object> source = sh.getSourceAsMap();
              callHierarchy.add(new ProcessInstanceReferenceDto()
                  .setInstanceId(String.valueOf(source.get(ID)))
                  .setProcessDefinitionId(String.valueOf(source.get(PROCESS_KEY)))
                  .setProcessDefinitionName(String.valueOf(source.getOrDefault(PROCESS_NAME, source.get(BPMN_PROCESS_ID)))));
            });
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance call hierarchy: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    callHierarchy.sort(Comparator.comparing(ref -> processInstanceIds.indexOf(ref.getInstanceId())));
    return callHierarchy;
  }

  /**
   * Searches for process instance by key.
   * @param processInstanceKey
   * @return
   */
  public ProcessInstanceForListViewEntity getProcessInstanceByKey(Long processInstanceKey) {
    try {
      return searchProcessInstanceByKey(processInstanceKey);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  protected ProcessInstanceForListViewEntity searchProcessInstanceByKey(Long processInstanceKey) throws IOException {
    final QueryBuilder query = joinWithAnd(
        idsQuery().addIds(String.valueOf(processInstanceKey)),
        termQuery(ListViewTemplate.PROCESS_INSTANCE_KEY,processInstanceKey)
    );

    SearchRequest request = ElasticsearchUtil.createSearchRequest(listViewTemplate, ALL)
      .source(new SearchSourceBuilder()
      .query(constantScoreQuery(query)));

    final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
    final SearchHits searchHits = response.getHits();
    if (searchHits.getTotalHits().value == 1 && searchHits.getHits().length == 1) {
        return ElasticsearchUtil.fromSearchHit(searchHits.getAt(0).getSourceAsString(), objectMapper,
                ProcessInstanceForListViewEntity.class);
    } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique process instance with id '%s'.", processInstanceKey));
    } else {
        throw new NotFoundException(String.format("Could not find process instance with id '%s'.", processInstanceKey));
    }
  }

  public ProcessInstanceCoreStatisticsDto getCoreStatistics() {
    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().size(0)
        .aggregation(INCIDENTS_AGGREGATION)
        .aggregation(RUNNING_AGGREGATION);
    if(permissionsService != null) {
      sourceBuilder.query(permissionsService.createQueryForProcessesByPermission(IdentityPermission.READ));
    }
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, ONLY_RUNTIME).source(sourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      Aggregations aggregations = response.getAggregations();
      long runningCount = ((SingleBucketAggregation) aggregations.get("running")).getDocCount();
      long incidentCount = ((SingleBucketAggregation) aggregations.get("incidents")).getDocCount();
      ProcessInstanceCoreStatisticsDto processInstanceCoreStatisticsDto = new ProcessInstanceCoreStatisticsDto().setRunning(runningCount)
          .setActive(runningCount - incidentCount).setWithIncidents(incidentCount);
      return processInstanceCoreStatisticsDto;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance core statistics: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public String getProcessInstanceTreePath(final String processInstanceId) {
    final QueryBuilder query = joinWithAnd(
        termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        termQuery(KEY, processInstanceId));
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(listViewTemplate)
        .source(new SearchSourceBuilder().query(query)
            .fetchSource(TREE_PATH, null));
    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value > 0) {
        return (String) response.getHits().getAt(0).getSourceAsMap()
            .get(TREE_PATH);
      } else {
        throw new OperateRuntimeException(
            String.format("Process instance not found: %s", processInstanceId));
      }
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while obtaining tree path for process instance: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }


}
