/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.archiver;

import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.scrollIdsToList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.exceptions.ArchiverException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This archiver job will delete all data related with process instances after they are finished:
 * flow node instances, "runtime" variables, process instances.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class ProcessInstanceArchiverJob extends AbstractArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstanceArchiverJob.class);

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private VariableIndex variableIndex;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private Archiver archiver;

  public ProcessInstanceArchiverJob(final List<Integer> partitionIds) {
    super(partitionIds);
  }

  @Override
  public int archiveBatch(ArchiveBatch archiveBatch) throws ArchiverException {
    if (archiveBatch != null) {
      LOGGER.debug("Following batch operations are found for archiving: {}", archiveBatch);
      archiver.deleteDocuments(
          variableIndex.getFullQualifiedName(),
          VariableIndex.PROCESS_INSTANCE_ID,
          archiveBatch.getIds());
      archiver.deleteDocuments(
          flowNodeInstanceIndex.getFullQualifiedName(),
          FlowNodeInstanceIndex.PROCESS_INSTANCE_ID,
          archiveBatch.getIds());
      archiver.deleteDocuments(
          processInstanceIndex.getFullQualifiedName(),
          ProcessInstanceIndex.ID,
          archiveBatch.getIds());
      return archiveBatch.getIds().size();
    } else {
      LOGGER.debug("Nothing to archive");
      return 0;
    }
  }

  @Override
  public ArchiveBatch getNextBatch() {

    final SearchRequest searchRequest = createFinishedProcessInstanceSearchRequest();

    try {
      final List<String> ids = runSearch(searchRequest);

      return createArchiveBatch(ids);
    } catch (Exception e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining finished batch operations: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  protected ArchiveBatch createArchiveBatch(final List<String> ids) {
    if (ids != null && ids.size() > 0) {
      return new ArchiveBatch(ids);
    } else {
      return null;
    }
  }

  private SearchRequest createFinishedProcessInstanceSearchRequest() {
    final QueryBuilder endDateQ =
        rangeQuery(ProcessInstanceIndex.END_DATE)
            .lte(tasklistProperties.getArchiver().getArchivingTimepoint());
    final TermsQueryBuilder partitionQ = termsQuery(TaskTemplate.PARTITION_ID, getPartitionIds());
    final ConstantScoreQueryBuilder q = constantScoreQuery(joinWithAnd(endDateQ, partitionQ));

    final SearchRequest searchRequest =
        new SearchRequest(processInstanceIndex.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .fetchSource(false)
                    .size(tasklistProperties.getArchiver().getRolloverBatchSize())
                    .sort(ProcessInstanceIndex.END_DATE, SortOrder.ASC))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug("Query finished process instances for archiving request: \n{}", q.toString());
    return searchRequest;
  }

  @Timed(value = Metrics.TIMER_NAME_ARCHIVER_QUERY, description = "Archiver: search query latency")
  private List<String> runSearch(SearchRequest searchRequest) throws IOException {
    final List<String> ids = scrollIdsToList(searchRequest, esClient);
    return ids;
  }
}
