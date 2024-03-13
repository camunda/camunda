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
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.schema.indices.ProcessIndex.BPMN_XML;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchProcessDefinitionDaoV1")
public class ElasticsearchProcessDefinitionDao extends ElasticsearchDao<ProcessDefinition>
    implements ProcessDefinitionDao {

  @Autowired private ProcessIndex processIndex;

  @Override
  public Results<ProcessDefinition> search(final Query<ProcessDefinition> query)
      throws APIException {
    logger.debug("search {}", query);
    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, ProcessDefinition.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest().indices(processIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        return new Results<ProcessDefinition>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(
                ElasticsearchUtil.mapSearchHits(
                    searchHitArray, objectMapper, ProcessDefinition.class))
            .setSortValues(sortValues);
      } else {
        return new Results<ProcessDefinition>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading process definitions", e);
    }
  }

  @Override
  public ProcessDefinition byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<ProcessDefinition> processDefinitions;
    try {
      processDefinitions =
          searchFor(new SearchSourceBuilder().query(termQuery(ProcessIndex.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in reading process definition for key %s", key), e);
    }
    if (processDefinitions.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No process definition found for key %s ", key));
    }
    if (processDefinitions.size() > 1) {
      throw new ServerException(
          String.format("Found more than one process definition for key %s", key));
    }
    return processDefinitions.get(0);
  }

  @Override
  public String xmlByKey(final Long key) throws APIException {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(processIndex.getAlias())
              .source(
                  new SearchSourceBuilder()
                      .query(termQuery(ProcessIndex.KEY, key))
                      .fetchSource(BPMN_XML, null));
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        final Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(BPMN_XML);
      }
    } catch (IOException e) {
      throw new ServerException(
          String.format("Error in reading process definition as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Process definition for key %s not found.", key));
  }

  protected void buildFiltering(
      final Query<ProcessDefinition> query, final SearchSourceBuilder searchSourceBuilder) {
    final ProcessDefinition filter = query.getFilter();
    if (filter != null) {
      final List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(ProcessDefinition.NAME, filter.getName()));
      queryBuilders.add(
          buildTermQuery(ProcessDefinition.BPMN_PROCESS_ID, filter.getBpmnProcessId()));
      queryBuilders.add(buildTermQuery(ProcessDefinition.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildTermQuery(ProcessDefinition.VERSION, filter.getVersion()));
      queryBuilders.add(buildTermQuery(ProcessDefinition.KEY, filter.getKey()));
      searchSourceBuilder.query(
          ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
    }
  }

  protected List<ProcessDefinition> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias()).source(searchSource);
    return tenantAwareClient.search(
        searchRequest,
        () -> {
          return ElasticsearchUtil.scroll(
              searchRequest, ProcessDefinition.class, objectMapper, elasticsearch);
        });
  }
}
