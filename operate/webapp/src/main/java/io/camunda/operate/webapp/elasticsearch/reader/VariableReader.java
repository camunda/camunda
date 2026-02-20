/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.SCOPE_KEY;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class VariableReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.VariableReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableReader.class);

  @Autowired private VariableTemplate variableTemplate;

  @Autowired private OperateProperties operateProperties;

  @Override
  public VariableDto getVariable(final String id) {
    try {
      final var query = ElasticsearchUtil.idsQuery(id);
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var searchRequest =
          new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
              .index(ElasticsearchUtil.whereToSearch(variableTemplate, ALL))
              .query(tenantAwareQuery)
              .build();

      final var response = esClient.search(searchRequest, VariableEntity.class);

      if (response.hits().total().value() != 1) {
        throw new NotFoundException(String.format("Variable with id %s not found.", id));
      }

      final var variableEntity = response.hits().hits().get(0).source();
      return VariableDto.createFrom(
          variableEntity,
          null,
          true,
          operateProperties.getImporter().getVariableSizeThreshold(),
          objectMapper);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variable: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public VariableDto getVariableByName(
      final String processInstanceId, final String scopeId, final String variableName) {

    try {
      final var query =
          ElasticsearchUtil.constantScoreQuery(
              joinWithAnd(
                  ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, processInstanceId),
                  ElasticsearchUtil.termsQuery(SCOPE_KEY, scopeId),
                  ElasticsearchUtil.termsQuery(NAME, variableName)));
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var searchRequest =
          new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
              .index(ElasticsearchUtil.whereToSearch(variableTemplate, ALL))
              .query(tenantAwareQuery)
              .build();

      final var response = esClient.search(searchRequest, VariableEntity.class);

      if (response.hits().total().value() > 0) {
        final var variableEntity = response.hits().hits().get(0).source();
        return VariableDto.createFrom(
            variableEntity,
            null,
            true,
            operateProperties.getImporter().getVariableSizeThreshold(),
            objectMapper);
      } else {
        return null;
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining variable for processInstanceId: %s, "
                  + "scopeId: %s, name: %s, error: %s",
              processInstanceId, scopeId, variableName, e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
