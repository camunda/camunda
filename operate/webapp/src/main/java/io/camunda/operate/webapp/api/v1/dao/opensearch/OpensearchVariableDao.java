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
import io.camunda.operate.webapp.api.v1.dao.VariableDao;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchVariableDao extends OpensearchKeyFilteringDao<Variable, Variable>
    implements VariableDao {

  private final VariableTemplate variableIndex;

  public OpensearchVariableDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final @Qualifier("operateVariableTemplate") VariableTemplate variableIndex) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.variableIndex = variableIndex;
  }

  @Override
  protected String getUniqueSortKey() {
    return Variable.KEY;
  }

  @Override
  protected Class<Variable> getInternalDocumentModelClass() {
    return Variable.class;
  }

  @Override
  protected String getIndexName() {
    return variableIndex.getAlias();
  }

  @Override
  protected void buildFiltering(final Query<Variable> query, final SearchRequest.Builder request) {
    final Variable filter = query.getFilter();

    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(Variable.KEY, filter.getKey()),
                  queryDSLWrapper.term(Variable.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.term(
                      Variable.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
                  queryDSLWrapper.term(Variable.SCOPE_KEY, filter.getScopeKey()),
                  queryDSLWrapper.term(Variable.NAME, filter.getName()),
                  queryDSLWrapper.term(Variable.VALUE, filter.getValue()),
                  queryDSLWrapper.term(Variable.TRUNCATED, filter.getTruncated()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected Variable convertInternalToApiResult(final Variable internalResult) {
    return internalResult;
  }

  @Override
  protected String getKeyFieldName() {
    return Variable.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(final Long key) {
    return String.format("Error in reading variable for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(final Long key) {
    return String.format("No variable found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(final Long key) {
    return String.format("Found more than one variables for key %s", key);
  }
}
