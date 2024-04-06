/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.schema.indices.ProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.util.ElasticsearchUtil.QueryType.ALL;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.ProcessInstanceStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessInstanceStoreElasticSearch implements ProcessInstanceStore {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceStoreElasticSearch.class);

  @Autowired ProcessInstanceIndex processInstanceIndex;

  @Autowired TaskStore taskStore;

  @Autowired List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired TaskVariableTemplate taskVariableTemplate;

  @Autowired RetryElasticsearchClient retryElasticsearchClient;

  @Autowired TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired TasklistProperties tasklistProperties;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public DeletionStatus deleteProcessInstance(final String processInstanceId) {
    if (tasklistProperties.getMultiTenancy().isEnabled() && getById(processInstanceId).isEmpty()) {
      return DeletionStatus.NOT_FOUND;
    }

    // Don't need to validate for canceled/completed process instances
    // because only completed will be imported by ProcessInstanceZeebeRecordProcessor
    final boolean processInstanceWasDeleted =
        retryElasticsearchClient.deleteDocument(
            processInstanceIndex.getFullQualifiedName(), processInstanceId);
    // if deleted -> process instance was really finished and we can delete dependent data
    if (processInstanceWasDeleted) {
      return deleteProcessInstanceDependantsFor(processInstanceId);
    } else {
      return DeletionStatus.NOT_FOUND;
    }
  }

  private DeletionStatus deleteProcessInstanceDependantsFor(String processInstanceId) {
    final List<String> dependantTaskIds = getDependantTasksIdsFor(processInstanceId);
    boolean deleted = false;
    for (ProcessInstanceDependant dependant : processInstanceDependants) {
      deleted =
          retryElasticsearchClient.deleteDocumentsByQuery(
                  dependant.getAllIndicesPattern(),
                  termQuery(PROCESS_INSTANCE_ID, processInstanceId))
              || deleted;
    }
    if (deleted) {
      deleteVariablesFor(dependantTaskIds);
      return DeletionStatus.DELETED;
    }
    return DeletionStatus.FAILED;
  }

  private List<String> getDependantTasksIdsFor(final String processInstanceId) {
    return taskStore.getTaskIdsByProcessInstanceId(processInstanceId);
  }

  private Optional<ProcessInstanceEntity> getById(String processInstanceId) {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(processInstanceIndex.getFullQualifiedName());
      final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
      sourceBuilder.query(QueryBuilders.termQuery(ProcessInstanceIndex.ID, processInstanceId));
      searchRequest.source(sourceBuilder);

      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits hits = searchResponse.getHits();
      if (hits.getTotalHits().value == 0) {
        return Optional.empty();
      }

      final SearchHit hit = hits.getAt(0);
      final String sourceAsString = hit.getSourceAsString();
      final ProcessInstanceEntity entity =
          objectMapper.readValue(sourceAsString, ProcessInstanceEntity.class);
      return Optional.of(entity);
    } catch (IOException e) {
      LOGGER.error(
          String.format("Error retrieving processInstance with ID [%s]", processInstanceId), e);
      return Optional.empty();
    }
  }

  private boolean deleteVariablesFor(final List<String> taskIds) {
    return retryElasticsearchClient.deleteDocumentsByQuery(
        ElasticsearchUtil.whereToSearch(taskVariableTemplate, ALL),
        termsQuery(TaskVariableTemplate.TASK_ID, taskIds));
  }
}
