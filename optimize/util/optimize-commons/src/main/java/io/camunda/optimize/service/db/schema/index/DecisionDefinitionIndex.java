/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.DatabaseConstants;

public abstract class DecisionDefinitionIndex<TBuilder> extends AbstractDefinitionIndex<TBuilder> {

  public static final int VERSION = 5;

  public static final String DECISION_DEFINITION_ID = DEFINITION_ID;
  public static final String DECISION_DEFINITION_KEY = DEFINITION_KEY;
  public static final String DECISION_DEFINITION_VERSION = DEFINITION_VERSION;
  public static final String DECISION_DEFINITION_VERSION_TAG = DEFINITION_VERSION_TAG;
  public static final String DECISION_DEFINITION_NAME = DEFINITION_NAME;
  public static final String DECISION_DEFINITION_XML = "dmn10Xml";
  public static final String TENANT_ID = DEFINITION_TENANT_ID;
  public static final String INPUT_VARIABLE_NAMES = "inputVariableNames";
  public static final String OUTPUT_VARIABLE_NAMES = "outputVariableNames";

  @Override
  public String getIndexName() {
    return DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return super.addProperties(builder)
        .properties(INPUT_VARIABLE_NAMES, p -> p.object(o -> o.enabled(false)))
        .properties(OUTPUT_VARIABLE_NAMES, p -> p.object(o -> o.enabled(false)))
        .properties(
            DECISION_DEFINITION_XML,
            p -> p.text(o -> o.index(true).analyzer("is_present_analyzer")));
  }
}
