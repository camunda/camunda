/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sourceInclude;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EXECUTION_INDEX;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_NAME;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import java.util.List;
import java.util.function.Consumer;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionInstanceReader implements DecisionInstanceReader {

  private final DecisionInstanceTemplate decisionInstanceTemplate;
  private final RichOpenSearchClient richOpenSearchClient;

  public OpensearchDecisionInstanceReader(
      final DecisionInstanceTemplate decisionInstanceTemplate,
      final RichOpenSearchClient richOpenSearchClient) {
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  @Override
  public Tuple<String, String> getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final String[] calledDecisionInstanceId = {null};
    final String[] calledDecisionDefinitionName = {null};
    findCalledDecisionInstance(
        flowNodeInstanceId,
        hit -> {
          final var source = hit.source();
          final var rootDecisionDefId = source.getRootDecisionDefinitionId();
          final var decisionDefId = source.getDecisionDefinitionId();
          if (rootDecisionDefId.equals(decisionDefId)) {
            calledDecisionInstanceId[0] = source.getId();
            var decisionName = source.getDecisionName();
            if (decisionName == null) {
              decisionName = source.getDecisionId();
            }
            calledDecisionDefinitionName[0] = decisionName;
          } else {
            var decisionName = source.getRootDecisionName();
            if (decisionName == null) {
              decisionName = source.getRootDecisionId();
            }
            calledDecisionDefinitionName[0] = decisionName;
          }
        });

    return Tuple.of(calledDecisionInstanceId[0], calledDecisionDefinitionName[0]);
  }

  private void findCalledDecisionInstance(
      final String flowNodeInstanceId,
      final Consumer<Hit<DecisionInstanceEntity>> decisionInstanceConsumer) {

    final var searchRequest =
        searchRequestBuilder(decisionInstanceTemplate.getAlias())
            .query(withTenantCheck(term(ELEMENT_INSTANCE_KEY, flowNodeInstanceId)))
            .source(
                sourceInclude(
                    ROOT_DECISION_DEFINITION_ID,
                    ROOT_DECISION_NAME,
                    ROOT_DECISION_ID,
                    DECISION_DEFINITION_ID,
                    DECISION_NAME,
                    DecisionIndex.DECISION_ID))
            .sort(
                List.of(
                    sortOptions(EVALUATION_DATE, SortOrder.Desc),
                    sortOptions(EXECUTION_INDEX, SortOrder.Desc)));
    final var response =
        richOpenSearchClient.doc().search(searchRequest, DecisionInstanceEntity.class);
    if (response.hits().total().value() >= 1) {
      decisionInstanceConsumer.accept(response.hits().hits().get(0));
    }
  }
}
