/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.LabelDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class VariableLabelIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(DefinitionVariableLabelsDto.Fields.definitionKey, p -> p.keyword(k -> k))
        .properties(
            DefinitionVariableLabelsDto.Fields.labels,
            p ->
                p.object(
                    k ->
                        k.properties(LabelDto.Fields.variableLabel, p2 -> p2.text(t -> t))
                            .properties(LabelDto.Fields.variableName, p2 -> p2.text(t -> t))
                            .properties(LabelDto.Fields.variableType, p2 -> p2.text(t -> t))));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
