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

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.ProcessInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.ChangeStatus;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchProcessInstanceDaoV1")
public class ElasticsearchProcessInstanceDao extends ElasticsearchDao<ProcessInstance>
    implements ProcessInstanceDao {

  @Autowired private ListViewTemplate processInstanceIndex;

  @Autowired private ProcessInstanceWriter processInstanceWriter;

  private List<ProcessInstance> mapSearchHits(final SearchHit[] searchHitArray) {
    final List<ProcessInstance> processInstances =
        ElasticsearchUtil.mapSearchHits(searchHitArray, objectMapper, ProcessInstance.class);

    if (processInstances != null) {
      for (final ProcessInstance pi : processInstances) {
        pi.setStartDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getStartDate()));
        pi.setEndDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getEndDate()));
      }
    }

    return processInstances;
  }

  @Override
  public Results<ProcessInstance> search(final Query<ProcessInstance> query) throws APIException {
    logger.debug("search {}", query);
    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, ProcessInstance.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest().indices(processInstanceIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        final List<ProcessInstance> processInstances = mapSearchHits(searchHitArray);
        return new Results<ProcessInstance>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(processInstances)
            .setSortValues(sortValues);
      } else {
        return new Results<ProcessInstance>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (final Exception e) {
      throw new ServerException("Error in reading process instances", e);
    }
  }

  @Override
  public ProcessInstance byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    final List<ProcessInstance> processInstances;
    try {
      processInstances =
          searchFor(new SearchSourceBuilder().query(termQuery(ListViewTemplate.KEY, key)));
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading process instance for key %s", key), e);
    }
    if (processInstances.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No process instances found for key %s ", key));
    }
    if (processInstances.size() > 1) {
      throw new ServerException(
          String.format("Found more than one process instances for key %s", key));
    }
    return processInstances.get(0);
  }

  @Override
  @PreAuthorize("hasPermission('write')")
  public ChangeStatus delete(final Long key) throws APIException {
    // Check for not exists
    byKey(key);
    try {
      processInstanceWriter.deleteInstanceById(key);
      return new ChangeStatus()
          .setDeleted(1)
          .setMessage(
              String.format("Process instance and dependant data deleted for key '%s'", key));
    } catch (final IllegalArgumentException iae) {
      throw new ClientException(iae.getMessage(), iae);
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in deleting process instance and dependant data for key '%s'", key),
          e);
    }
  }

  @Override
  protected void buildFiltering(
      final Query<ProcessInstance> query, final SearchSourceBuilder searchSourceBuilder) {
    final ProcessInstance filter = query.getFilter();
    final List<QueryBuilder> queryBuilders = new ArrayList<>();
    queryBuilders.add(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    if (filter != null) {
      queryBuilders.add(buildTermQuery(ProcessInstance.KEY, filter.getKey()));
      queryBuilders.add(
          buildTermQuery(ProcessInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      queryBuilders.add(buildTermQuery(ProcessInstance.PARENT_KEY, filter.getParentKey()));
      queryBuilders.add(
          buildTermQuery(
              ProcessInstance.PARENT_FLOW_NODE_INSTANCE_KEY,
              filter.getParentFlowNodeInstanceKey()));
      queryBuilders.add(buildTermQuery(ProcessInstance.VERSION, filter.getProcessVersion()));
      queryBuilders.add(buildTermQuery(ProcessInstance.BPMN_PROCESS_ID, filter.getBpmnProcessId()));
      queryBuilders.add(buildTermQuery(ProcessInstance.STATE, filter.getState()));
      queryBuilders.add(buildTermQuery(ProcessInstance.INCIDENT, filter.getIncident()));
      queryBuilders.add(buildTermQuery(ProcessInstance.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildMatchDateQuery(ProcessInstance.START_DATE, filter.getStartDate()));
      queryBuilders.add(buildMatchDateQuery(ProcessInstance.END_DATE, filter.getEndDate()));
    }
    searchSourceBuilder.query(joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
  }

  protected List<ProcessInstance> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(processInstanceIndex.getAlias()).source(searchSource);
    final List<ProcessInstance> processInstances =
        tenantAwareClient.search(
            searchRequest,
            () -> {
              return ElasticsearchUtil.scroll(
                  searchRequest, ProcessInstance.class, objectMapper, elasticsearch);
            });

    if (processInstances != null) {
      for (final ProcessInstance pi : processInstances) {
        pi.setStartDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getStartDate()));
        pi.setEndDate(dateTimeFormatter.convertGeneralToApiDateTime(pi.getEndDate()));
      }
    }

    return processInstances;
  }
}
