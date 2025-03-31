/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchProcessDefinitionDao
    extends OpensearchKeyFilteringDao<ProcessDefinition, ProcessDefinition>
    implements ProcessDefinitionDao {

  private final ProcessIndex processIndex;

  public OpensearchProcessDefinitionDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final @Qualifier("operateProcessIndex") ProcessIndex processIndex) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.processIndex = processIndex;
  }

  @Override
  protected String getKeyFieldName() {
    return ProcessIndex.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(final Long key) {
    return String.format("Error in reading process definition for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(final Long key) {
    return String.format("No process definition found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(final Long key) {
    return String.format("Found more than one process definition for key %s", key);
  }

  @Override
  public String xmlByKey(final Long key) throws APIException {
    validateKey(key);
    final var request =
        requestDSLWrapper
            .searchRequestBuilder(processIndex.getAlias())
            .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.term(ProcessIndex.KEY, key)))
            .source(queryDSLWrapper.sourceInclude(ProcessIndex.BPMN_XML));
    try {
      final var response = richOpenSearchClient.doc().search(request, Map.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source().get(ProcessIndex.BPMN_XML).toString();
      }
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading process definition as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Process definition for key %s not found.", key));
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
  protected String getIndexName() {
    return processIndex.getAlias();
  }

  @Override
  protected void buildFiltering(
      final Query<ProcessDefinition> query, final SearchRequest.Builder request) {
    final ProcessDefinition filter = query.getFilter();
    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(ProcessDefinition.NAME, filter.getName()),
                  queryDSLWrapper.term(
                      ProcessDefinition.BPMN_PROCESS_ID, filter.getBpmnProcessId()),
                  queryDSLWrapper.term(ProcessDefinition.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.term(ProcessDefinition.VERSION, filter.getVersion()),
                  queryDSLWrapper.term(ProcessDefinition.KEY, filter.getKey()),
                  queryDSLWrapper.term(ProcessDefinition.VERSION_TAG, filter.getVersionTag()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected ProcessDefinition convertInternalToApiResult(final ProcessDefinition internalResult) {
    return internalResult;
  }
}
