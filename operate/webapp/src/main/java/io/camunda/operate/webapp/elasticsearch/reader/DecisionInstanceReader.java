/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.MAP_CLASS;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EXECUTION_INDEX;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_NAME;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class DecisionInstanceReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.DecisionInstanceReader {

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Override
  public Tuple<String, String> getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final var query = ElasticsearchUtil.termsQuery(ELEMENT_INSTANCE_KEY, flowNodeInstanceId);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
    final var searchRequest =
        new SearchRequest.Builder()
            .index(whereToSearch(decisionInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .source(
                s ->
                    s.filter(
                        f ->
                            f.includes(
                                ROOT_DECISION_DEFINITION_ID,
                                ROOT_DECISION_NAME,
                                ROOT_DECISION_ID,
                                DECISION_DEFINITION_ID,
                                DECISION_NAME,
                                DecisionIndex.DECISION_ID)))
            .sort(ElasticsearchUtil.sortOrder(EVALUATION_DATE, SortOrder.Desc))
            .sort(ElasticsearchUtil.sortOrder(EXECUTION_INDEX, SortOrder.Desc))
            .size(1)
            .build();

    try {
      final var response = esClient.search(searchRequest, MAP_CLASS);
      if (response.hits().total().value() >= 1) {
        final var decisionInstanceHit = response.hits().hits().getFirst();
        final var decisionInstanceMap = decisionInstanceHit.source();

        if (decisionInstanceMap == null) {
          throw new OperateRuntimeException(
              String.format(
                  "Elasticsearch document for decision instance with id '%s' has no source.",
                  decisionInstanceHit.id()));
        }

        return buildCalledDecisionTuple(decisionInstanceHit.id(), decisionInstanceMap);
      }
      return Tuple.of(null, null);
    } catch (final IOException e) {
      final var message =
          String.format(
              "Exception occurred, while obtaining called decision instance id for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private Tuple<String, String> buildCalledDecisionTuple(
      final String hitId, final Map<String, Object> source) {
    final var rootDecisionDefId = (String) source.get(ROOT_DECISION_DEFINITION_ID);
    final var decisionDefId = (String) source.get(DECISION_DEFINITION_ID);
    final var isRootDecision = Objects.equals(rootDecisionDefId, decisionDefId);

    final var instanceId = isRootDecision ? hitId : null;
    final var nameField = isRootDecision ? DECISION_NAME : ROOT_DECISION_NAME;
    final var fallbackField = isRootDecision ? DecisionIndex.DECISION_ID : ROOT_DECISION_ID;
    final var definitionName = (String) source.getOrDefault(nameField, source.get(fallbackField));

    return Tuple.of(instanceId, definitionName);
  }
}
