/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class DecisionReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.DecisionReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionReader.class);

  @Autowired private DecisionIndex decisionIndex;

  /**
   * Gets the decision by key
   *
   * @param decisionDefinitionKey decisionDefinitionKey
   * @return decision
   */
  @Override
  public DecisionDefinitionEntity getDecision(final Long decisionDefinitionKey) {
    final var query = ElasticsearchUtil.termsQuery(DecisionIndex.KEY, decisionDefinitionKey);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
    final var searchRequest =
        new SearchRequest.Builder().index(decisionIndex.getAlias()).query(tenantAwareQuery).build();

    try {
      final var response = esClient.search(searchRequest, DecisionDefinitionEntity.class);
      final var totalHits = response.hits().total().value();
      if (totalHits == 1) {
        final var hit = response.hits().hits().get(0);
        final var source = hit.source();
        if (source == null) {
          throw new OperateRuntimeException(
              String.format(
                  "Decision source is missing for key '%s' despite a search hit being returned.",
                  decisionDefinitionKey));
        }
        return source;
      } else if (totalHits > 1) {
        throw new NotFoundException(
            String.format("Could not find unique decision with key '%s'.", decisionDefinitionKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find decision with key '%s'.", decisionDefinitionKey));
      }
    } catch (final IOException e) {
      final var message =
          String.format("Exception occurred, while obtaining the decision: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}
