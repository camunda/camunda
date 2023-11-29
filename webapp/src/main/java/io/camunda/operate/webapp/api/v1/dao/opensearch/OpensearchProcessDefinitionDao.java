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

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchProcessDefinitionDao extends OpensearchKeyFilteringDao<ProcessDefinition, ProcessDefinition> implements ProcessDefinitionDao {

  private final ProcessIndex processIndex;
  public OpensearchProcessDefinitionDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                        RichOpenSearchClient richOpenSearchClient, ProcessIndex processIndex) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.processIndex = processIndex;
  }



  @Override
  protected String getKeyFieldName() {
    return ProcessIndex.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading process definition for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No process definition found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one process definition for key %s", key);
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
  protected String getIndexName() {
    return processIndex.getAlias();
  }

  @Override
  protected String getUniqueSortKey() {
    return ProcessIndex.KEY;
  }

  @Override
  protected Class<ProcessDefinition> getInternalDocumentModelClass() {
    return ProcessDefinition.class;
  }

  @Override
  protected void buildFiltering(Query<ProcessDefinition> query, SearchRequest.Builder request) {
    final ProcessDefinition filter = query.getFilter();
    if (filter != null) {
      var queryTerms = Stream.of(
          queryDSLWrapper.term(ProcessDefinition.NAME, filter.getName()),
          queryDSLWrapper.term(ProcessDefinition.BPMN_PROCESS_ID, filter.getBpmnProcessId()),
          queryDSLWrapper.term(ProcessDefinition.TENANT_ID, filter.getTenantId()),
          queryDSLWrapper.term(ProcessDefinition.VERSION, filter.getVersion()),
          queryDSLWrapper.term(ProcessDefinition.KEY, filter.getKey())
      ).filter(Objects::nonNull).collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected ProcessDefinition convertInternalToApiResult(ProcessDefinition internalResult) {
    return internalResult;
  }
}
