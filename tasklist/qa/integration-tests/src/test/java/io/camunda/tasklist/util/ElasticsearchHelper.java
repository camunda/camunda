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
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ElasticsearchUtil.*;
import static org.elasticsearch.index.query.QueryBuilders.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchHelper implements NoSqlHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchHelper.class);

  private static final Integer QUERY_SIZE = 100;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private VariableIndex variableIndex;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  public TaskEntity getTask(String taskId) {
    try {
      final GetRequest getRequest = new GetRequest(taskTemplate.getAlias()).id(taskId);
      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (response.isExists()) {
        return fromSearchHit(response.getSourceAsString(), objectMapper, TaskEntity.class);
      } else {
        throw new NotFoundApiException(
            String.format("Could not find  task for taskId [%s].", taskId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the task: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public ProcessInstanceEntity getProcessInstance(String processInstanceId) {
    try {
      final GetRequest getRequest =
          new GetRequest(processInstanceIndex.getAlias()).id(processInstanceId);
      final GetResponse response = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (response.isExists()) {
        return fromSearchHit(
            response.getSourceAsString(), objectMapper, ProcessInstanceEntity.class);
      } else {
        throw new NotFoundApiException(
            String.format("Could not find task for processInstanceId [%s].", processInstanceId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public List<ProcessInstanceEntity> getProcessInstances(List<String> processInstanceIds) {
    try {
      final SearchRequest request =
          new SearchRequest(processInstanceIndex.getAlias())
              .source(
                  new SearchSourceBuilder()
                      .query(idsQuery().addIds(processInstanceIds.toArray(String[]::new))));
      final SearchResponse searchResponse = esClient.search(request, RequestOptions.DEFAULT);
      return scroll(request, ProcessInstanceEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining list of processes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public List<TaskEntity> getTask(String processInstanceId, String flowNodeBpmnId) {
    TermQueryBuilder piId = null;
    if (processInstanceId != null) {
      piId = termQuery(TaskTemplate.PROCESS_INSTANCE_ID, processInstanceId);
    }
    final SearchRequest searchRequest =
        new SearchRequest(taskTemplate.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            piId, termQuery(TaskTemplate.FLOW_NODE_BPMN_ID, flowNodeBpmnId)))
                    .sort(TaskTemplate.CREATION_TIME, SortOrder.DESC));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value >= 1) {
        return mapSearchHits(response.getHits().getHits(), objectMapper, TaskEntity.class);
      } else {
        throw new NotFoundApiException(
            String.format(
                "Could not find task for processInstanceId [%s] with flowNodeBpmnId [%s].",
                processInstanceId, flowNodeBpmnId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public boolean checkVariableExists(final String taskId, final String varName) {
    final TermQueryBuilder taskIdQ = termQuery(TaskVariableTemplate.TASK_ID, taskId);
    final TermQueryBuilder varNameQ = termQuery(TaskVariableTemplate.NAME, varName);
    final SearchRequest searchRequest =
        new SearchRequest(taskVariableTemplate.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(joinWithAnd(taskIdQ, varNameQ))));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value > 0;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public boolean checkVariablesExist(final String[] varNames) {
    final SearchRequest searchRequest =
        new SearchRequest(variableIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(termsQuery(VariableIndex.NAME, varNames))));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value == varNames.length;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public List<String> getIdsFromIndex(
      final String idFieldName, final String index, final List<String> ids) {
    final TermsQueryBuilder q = termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
    final SearchRequest request =
        new SearchRequest(index).source(new SearchSourceBuilder().query(q).size(QUERY_SIZE));

    try {
      final List<String> idsFromEls =
          ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient);
      return idsFromEls;
    } catch (IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  public List<TaskEntity> getTasksFromIdAndIndex(final String index, final List<String> ids) {
    final TermsQueryBuilder q =
        termsQuery(TaskTemplate.ID, CollectionUtil.toSafeArrayOfStrings(ids));
    final SearchRequest searchRequest =
        new SearchRequest(index).source(new SearchSourceBuilder().query(q).size(QUERY_SIZE));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return mapSearchHits(response.getHits().getHits(), objectMapper, TaskEntity.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<TaskEntity> getAllTasks(String index) {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(index)
              .source(
                  new SearchSourceBuilder().query(constantScoreQuery(matchAllQuery())).size(100));
      final SearchResponse response;
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchUtil.mapSearchHits(
          response.getHits().getHits(), objectMapper, TaskEntity.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Long countIndexResult(final String index) {
    try {
      final QueryBuilder query = matchAllQuery();
      final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(query);
      searchSourceBuilder.fetchSource(false);
      final SearchResponse searchResponse =
          esClient.search(
              new SearchRequest(index).source(searchSourceBuilder), RequestOptions.DEFAULT);
      return searchResponse.getHits().getTotalHits().value;
    } catch (IOException e) {
      return -1L;
    }
  }

  @Override
  public Boolean isIndexDynamicMapping(IndexDescriptor indexDescriptor, final String dynamic)
      throws IOException {
    final Map<String, MappingMetadata> mappings =
        esClient
            .indices()
            .get(
                new GetIndexRequest(indexDescriptor.getFullQualifiedName()), RequestOptions.DEFAULT)
            .getMappings();
    final MappingMetadata mappingMetadata = mappings.get(indexDescriptor.getFullQualifiedName());
    return mappingMetadata.getSourceAsMap().get("dynamic").equals(dynamic);
  }

  @Override
  public Map<String, Object> getFieldDescription(IndexDescriptor indexDescriptor)
      throws IOException {
    final Map<String, MappingMetadata> mappings =
        esClient
            .indices()
            .get(
                new GetIndexRequest(indexDescriptor.getFullQualifiedName()), RequestOptions.DEFAULT)
            .getMappings();
    final Map<String, Object> source =
        mappings.get(indexDescriptor.getFullQualifiedName()).getSourceAsMap();
    return (Map<String, Object>) source.get("properties");
  }

  @Override
  public Boolean indexHasAlias(String index, String alias) throws IOException {
    final GetIndexResponse getIndexResponse =
        esClient.indices().get(new GetIndexRequest(index), RequestOptions.DEFAULT);
    return getIndexResponse.getAliases().size() == 1
        && getIndexResponse.getAliases().get(index).get(0).alias().equals(alias);
  }

  @Override
  public void delete(String index, String id) throws IOException {
    final DeleteRequest request = new DeleteRequest().index(index).id(id);
    esClient.delete(request, RequestOptions.DEFAULT);
  }

  @Override
  public void update(String index, String id, Map<String, Object> jsonMap) throws IOException {
    final UpdateRequest request = new UpdateRequest().index(index).id(id).doc(jsonMap);
    esClient.update(request, RequestOptions.DEFAULT);
  }
}
