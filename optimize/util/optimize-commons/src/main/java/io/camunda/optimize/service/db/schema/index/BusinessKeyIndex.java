/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class BusinessKeyIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 2;

  public static final String BUSINESS_KEY = BusinessKeyDto.Fields.businessKey;
  public static final String PROCESS_INSTANCE_ID = BusinessKeyDto.Fields.processInstanceId;

  @Override
  public String getIndexName() {
    return DatabaseConstants.BUSINESS_KEY_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(PROCESS_INSTANCE_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(BUSINESS_KEY, Property.of(p -> p.keyword(k -> k)));
  }
}
