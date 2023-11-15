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
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchVariableDao extends OpensearchDao<Variable> implements VariableDao {

  private final VariableTemplate variableIndex;

  public OpensearchVariableDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                               VariableTemplate variableIndex, RichOpenSearchClient richOpenSearchClient) {
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
  protected Class<Variable> getModelClass() {
    return Variable.class;
  }

  @Override
  public Variable byKey(Long key) throws APIException {
    List<Variable> variables;
    try {
      variables = search(new Query<Variable>().setFilter(new Variable().setKey(key))).getItems();
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading variable for key %s", key), e);
    }
    if (variables == null || variables.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No variable found for key %s ", key));
    }
    if (variables.size() > 1) {
      throw new ServerException(String.format("Found more than one variables for key %s", key));
    }
    return variables.get(0);
  }

  @Override
  protected void buildFiltering(Query<Variable> query, SearchRequest.Builder request) {
    Variable filter = query.getFilter();

    if (filter != null) {
      List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms = new LinkedList<>();
      if (filter.getKey() != null) {
        queryTerms.add(queryDSLWrapper.term(Variable.KEY, filter.getKey()));
      }
      if (filter.getTenantId() != null) {
        queryTerms.add(queryDSLWrapper.term(Variable.TENANT_ID, filter.getTenantId()));
      }
      if (filter.getProcessInstanceKey() != null) {
        queryTerms.add(queryDSLWrapper.term(Variable.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
      }
      if (filter.getScopeKey() != null) {
        queryTerms.add(queryDSLWrapper.term(Variable.SCOPE_KEY, filter.getScopeKey()));
      }
      if (filter.getName() != null) {
        queryTerms.add(queryDSLWrapper.term(Variable.NAME, filter.getName()));
      }
      if (filter.getValue() != null) {
        queryTerms.add(queryDSLWrapper.term(Variable.VALUE, filter.getValue()));
      }
      if (filter.getTruncated() != null) {
        queryTerms.add(queryDSLWrapper.term(Variable.TRUNCATED, filter.getTruncated()));
      }

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }
}
