/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchProcessDefinitionDao extends OpensearchDao<ProcessDefinition> implements ProcessDefinitionDao {

  private final ProcessIndex processIndex;
  public OpensearchProcessDefinitionDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                        ProcessIndex processIndex, RichOpenSearchClient richOpenSearchClient) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.processIndex = processIndex;
  }

  @Override
  public ProcessDefinition byKey(Long key) throws APIException {
    validateKey(key);
    var request = requestDSLWrapper.searchRequestBuilder(processIndex.getAlias())
        .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.term(ProcessIndex.KEY, key)));

    List<ProcessDefinition> processDefinitions;
    try {
      processDefinitions = richOpenSearchClient.doc().searchValues(request, ProcessDefinition.class);
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading process definition for key %s", key), e);
    }
    if (processDefinitions.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No process definition found for key %s ", key));
    }
    if (processDefinitions.size() > 1) {
      throw new ServerException(String.format("Found more than one process definition for key %s", key));
    }
    return processDefinitions.get(0);
  }

  @Override
  public String xmlByKey(Long key) throws APIException {
    validateKey(key);
    var request =requestDSLWrapper.searchRequestBuilder(processIndex.getAlias())
        .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.term(ProcessIndex.KEY, key)))
        .source(queryDSLWrapper.sourceInclude(ProcessIndex.BPMN_XML));
    try {
      var response = richOpenSearchClient.doc().search(request, Map.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source().get(ProcessIndex.BPMN_XML).toString();
      }
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading process definition as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(String.format("Process definition for key %s not found.", key));
  }

  @Override
  protected SearchRequest.Builder buildRequest(Query<ProcessDefinition> query) {
    return requestDSLWrapper.searchRequestBuilder(getIndexName());
  }

  @Override
  protected String getIndexName() {
    return processIndex.getAlias();
  }

  @Override
  protected String getUniqueSortKey() {
    return ProcessIndex.KEY;
  }

  @Override
  protected Class<ProcessDefinition> getModelClass() {
    return ProcessDefinition.class;
  }

  @Override
  protected void buildFiltering(Query<ProcessDefinition> query, SearchRequest.Builder request) {
    final ProcessDefinition filter = query.getFilter();
    if (filter != null) {
      var queries = new ArrayList<org.opensearch.client.opensearch._types.query_dsl.Query>();
      queries.add(buildTermQuery(ProcessDefinition.NAME, filter.getName()));
      queries.add(buildTermQuery(ProcessDefinition.BPMN_PROCESS_ID, filter.getBpmnProcessId()));
      queries.add(buildTermQuery(ProcessDefinition.TENANT_ID, filter.getTenantId()));
      queries.add(buildTermQuery(ProcessDefinition.VERSION, filter.getVersion()));
      queries.add(buildTermQuery(ProcessDefinition.KEY, filter.getKey()));
      request.query(
          queryDSLWrapper.withTenantCheck(queryDSLWrapper.and(queries)));
    }
  }

  private void validateKey(Long key) {
    if (key == null) throw new ServerException("No process definition key provided");
  }
}
