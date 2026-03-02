/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.exception.NotFoundException;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionReader implements DecisionReader {

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public DecisionDefinitionEntity getDecision(final Long decisionDefinitionKey) {
    final var request =
        searchRequestBuilder(decisionIndex.getAlias())
            .query(withTenantCheck(term(DecisionIndex.KEY, decisionDefinitionKey)));
    final var hits =
        richOpenSearchClient.doc().search(request, DecisionDefinitionEntity.class).hits();
    if (hits.total().value() == 1) {
      return hits.hits().get(0).source();
    } else if (hits.total().value() > 1) {
      throw new NotFoundException(
          String.format("Could not find unique decision with key '%s'.", decisionDefinitionKey));
    } else {
      throw new NotFoundException(
          String.format("Could not find decision with key '%s'.", decisionDefinitionKey));
    }
  }
}
