/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.VariableDao;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchVariableDao extends OpensearchKeyFilteringDao<Variable, Variable> implements VariableDao {

  private final VariableTemplate variableIndex;

  public OpensearchVariableDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                               RichOpenSearchClient richOpenSearchClient, VariableTemplate variableIndex) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.variableIndex = variableIndex;
  }

  @Override
  protected String getIndexName() {
    return variableIndex.getAlias();
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
  protected String getKeyFieldName() {
    return Variable.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading variable for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No variable found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one variables for key %s", key);
  }

  @Override
  protected void buildFiltering(Query<Variable> query, SearchRequest.Builder request) {
    Variable filter = query.getFilter();

    if (filter != null) {
      var queryTerms = Stream.of(
          queryDSLWrapper.term(Variable.KEY, filter.getKey()),
          queryDSLWrapper.term(Variable.TENANT_ID, filter.getTenantId()),
          queryDSLWrapper.term(Variable.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
          queryDSLWrapper.term(Variable.SCOPE_KEY, filter.getScopeKey()),
          queryDSLWrapper.term(Variable.NAME, filter.getName()),
          queryDSLWrapper.term(Variable.VALUE, filter.getValue()),
          queryDSLWrapper.term(Variable.TRUNCATED, filter.getTruncated())
      ).filter(Objects::nonNull).collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected Variable convertInternalToApiResult(Variable internalResult) {
    return internalResult;
  }
}
