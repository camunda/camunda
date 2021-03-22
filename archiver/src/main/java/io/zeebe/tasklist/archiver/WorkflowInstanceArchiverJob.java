/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.archiver;

import static io.zeebe.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.zeebe.tasklist.util.ElasticsearchUtil.scrollIdsToList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.micrometer.core.annotation.Timed;
import io.zeebe.tasklist.Metrics;
import io.zeebe.tasklist.exceptions.ArchiverException;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.zeebe.tasklist.schema.indices.VariableIndex;
import io.zeebe.tasklist.schema.indices.WorkflowInstanceIndex;
import io.zeebe.tasklist.schema.templates.TaskTemplate;
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
 * This archiver job will delete all data related with workflow instances after they are finished:
 * flow node instances, "runtime" variables, workflow instances.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class WorkflowInstanceArchiverJob extends AbstractArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowInstanceArchiverJob.class);

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private VariableIndex variableIndex;

  @Autowired private WorkflowInstanceIndex workflowInstanceIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private Archiver archiver;

  public WorkflowInstanceArchiverJob(final List<Integer> partitionIds) {
    super(partitionIds);
  }

  @Override
  public int archiveBatch(ArchiveBatch archiveBatch) throws ArchiverException {
    if (archiveBatch != null) {
      LOGGER.debug("Following batch operations are found for archiving: {}", archiveBatch);
      archiver.deleteDocuments(
          variableIndex.getFullQualifiedName(),
          VariableIndex.WORKFLOW_INSTANCE_ID,
          archiveBatch.getIds());
      archiver.deleteDocuments(
          flowNodeInstanceIndex.getFullQualifiedName(),
          FlowNodeInstanceIndex.WORKFLOW_INSTANCE_ID,
          archiveBatch.getIds());
      archiver.deleteDocuments(
          workflowInstanceIndex.getFullQualifiedName(),
          WorkflowInstanceIndex.ID,
          archiveBatch.getIds());
      return archiveBatch.getIds().size();
    } else {
      LOGGER.debug("Nothing to archive");
      return 0;
    }
  }

  @Override
  public ArchiveBatch getNextBatch() {

    final SearchRequest searchRequest = createFinishedWorkflowInstanceSearchRequest();

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

  private SearchRequest createFinishedWorkflowInstanceSearchRequest() {
    final QueryBuilder endDateQ =
        rangeQuery(WorkflowInstanceIndex.END_DATE)
            .lte(tasklistProperties.getArchiver().getArchivingTimepoint());
    final TermsQueryBuilder partitionQ = termsQuery(TaskTemplate.PARTITION_ID, getPartitionIds());
    final ConstantScoreQueryBuilder q = constantScoreQuery(joinWithAnd(endDateQ, partitionQ));

    final SearchRequest searchRequest =
        new SearchRequest(workflowInstanceIndex.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(q)
                    .fetchSource(false)
                    .size(tasklistProperties.getArchiver().getRolloverBatchSize())
                    .sort(WorkflowInstanceIndex.END_DATE, SortOrder.ASC))
            .requestCache(false); // we don't need to cache this, as each time we need new data

    LOGGER.debug("Query finished workflow instances for archiving request: \n{}", q.toString());
    return searchRequest;
  }

  @Timed(value = Metrics.TIMER_NAME_ARCHIVER_QUERY, description = "Archiver: search query latency")
  private List<String> runSearch(SearchRequest searchRequest) throws IOException {
    final List<String> ids = scrollIdsToList(searchRequest, esClient);
    return ids;
  }
}
