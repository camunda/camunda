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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchVariableReader implements VariableReader {

  @Autowired private OperateProperties operateProperties;

  @Autowired private VariableTemplate variableTemplate;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public VariableDto getVariable(final String id) {
    final var searchRequest =
        searchRequestBuilder(variableTemplate).query(withTenantCheck(ids(id)));
    final var hits = richOpenSearchClient.doc().search(searchRequest, VariableEntity.class).hits();
    if (hits.total().value() != 1) {
      throw new NotFoundException(String.format("Variable with id %s not found.", id));
    }
    return toVariableDto(hits.hits().get(0).source());
  }

  @Override
  public VariableDto getVariableByName(
      final String processInstanceId, final String scopeId, final String variableName) {
    final var searchRequest =
        searchRequestBuilder(variableTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            term(ProcessInstanceDependant.PROCESS_INSTANCE_KEY, processInstanceId),
                            term(VariableTemplate.SCOPE_KEY, scopeId),
                            term(VariableTemplate.NAME, variableName)))));
    final var hits = richOpenSearchClient.doc().search(searchRequest, VariableEntity.class).hits();
    if (hits.total().value() > 0) {
      return toVariableDto(hits.hits().get(0).source());
    } else {
      return null;
    }
  }

  private VariableDto toVariableDto(final VariableEntity variableEntity) {
    return VariableDto.createFrom(
        variableEntity,
        null,
        true,
        operateProperties.getImporter().getVariableSizeThreshold(),
        objectMapper);
  }
}
