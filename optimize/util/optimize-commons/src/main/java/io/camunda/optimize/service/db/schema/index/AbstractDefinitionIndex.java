/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class AbstractDefinitionIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {
  public static final String DEFINITION_ID = DefinitionOptimizeResponseDto.Fields.id;
  public static final String DEFINITION_KEY = DefinitionOptimizeResponseDto.Fields.key;
  public static final String DEFINITION_VERSION = DefinitionOptimizeResponseDto.Fields.version;
  public static final String DEFINITION_VERSION_TAG =
      DefinitionOptimizeResponseDto.Fields.versionTag;
  public static final String DEFINITION_NAME = DefinitionOptimizeResponseDto.Fields.name;
  public static final String DATA_SOURCE = DefinitionOptimizeResponseDto.Fields.dataSource;
  public static final String DEFINITION_TENANT_ID = DefinitionOptimizeResponseDto.Fields.tenantId;
  public static final String DEFINITION_DELETED = DefinitionOptimizeResponseDto.Fields.deleted;

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(DEFINITION_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(DEFINITION_KEY, Property.of(p -> p.keyword(k -> k)))
        .properties(DEFINITION_VERSION, Property.of(p -> p.keyword(k -> k)))
        .properties(DEFINITION_VERSION_TAG, Property.of(p -> p.keyword(k -> k)))
        .properties(DATA_SOURCE, Property.of(p -> p.object(o -> o.dynamic(DynamicMapping.True))))
        .properties(DEFINITION_TENANT_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(DEFINITION_NAME, Property.of(p -> p.keyword(k -> k)))
        .properties(DEFINITION_DELETED, Property.of(p -> p.boolean_(k -> k)));
  }
}
