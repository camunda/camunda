/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import java.util.Locale;

public abstract class DecisionInstanceIndex<TBuilder> extends AbstractInstanceIndex<TBuilder> {

  public static final int VERSION = 5;

  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";

  public static final String DECISION_DEFINITION_ID = "decisionDefinitionId";
  public static final String DECISION_DEFINITION_KEY = "decisionDefinitionKey";
  public static final String DECISION_DEFINITION_VERSION = "decisionDefinitionVersion";

  public static final String DECISION_INSTANCE_ID = "decisionInstanceId";

  public static final String EVALUATION_DATE_TIME = "evaluationDateTime";

  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String ROOT_PROCESS_INSTANCE_ID = "rootProcessInstanceId";

  public static final String ACTIVITY_ID = "activityId";

  public static final String COLLECT_RESULT_VALUE = "collectResultValue";

  public static final String ROOT_DECISION_INSTANCE_ID = "rootDecisionInstanceId";

  public static final String INPUTS = "inputs";
  public static final String VARIABLE_ID = "id";
  public static final String VARIABLE_CLAUSE_ID = "clauseId";
  public static final String VARIABLE_CLAUSE_NAME = "clauseName";
  public static final String VARIABLE_VALUE_TYPE = "type";
  public static final String VARIABLE_VALUE = "value";

  public static final String OUTPUTS = "outputs";
  public static final String OUTPUT_VARIABLE_RULE_ID = "ruleId";
  public static final String OUTPUT_VARIABLE_RULE_ORDER = "ruleOrder";
  public static final String OUTPUT_VARIABLE_NAME = "variableName";

  public static final String MATCHED_RULES = "matchedRules";

  public static final String ENGINE = "engine";
  public static final String TENANT_ID = "tenantId";

  private final String indexName;

  protected DecisionInstanceIndex(final String decisionDefinitionKey) {
    super(decisionDefinitionKey);
    indexName = constructIndexName(decisionDefinitionKey);
  }

  public static String constructIndexName(final String decisionDefinitionKey) {
    return DECISION_INSTANCE_INDEX_PREFIX + decisionDefinitionKey.toLowerCase(Locale.ENGLISH);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public String getDefinitionKeyFieldName() {
    return DECISION_DEFINITION_KEY;
  }

  @Override
  public String getDefinitionVersionFieldName() {
    return DECISION_DEFINITION_VERSION;
  }

  @Override
  public String getTenantIdFieldName() {
    return TENANT_ID;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(DECISION_INSTANCE_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(DECISION_DEFINITION_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(DECISION_DEFINITION_KEY, Property.of(p -> p.keyword(k -> k)))
        .properties(DECISION_DEFINITION_VERSION, Property.of(p -> p.keyword(k -> k)))
        .properties(PROCESS_DEFINITION_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(PROCESS_DEFINITION_KEY, Property.of(p -> p.keyword(k -> k)))
        .properties(PROCESS_INSTANCE_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(ROOT_PROCESS_INSTANCE_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(
            EVALUATION_DATE_TIME, Property.of(p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT))))
        .properties(ACTIVITY_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(
            INPUTS,
            Property.of(
                p ->
                    p.nested(
                        k ->
                            k.properties(VARIABLE_ID, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    VARIABLE_CLAUSE_ID, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    VARIABLE_CLAUSE_NAME, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    VARIABLE_VALUE_TYPE, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    VARIABLE_VALUE,
                                    Property.of(p1 -> p1.keyword(this::addValueMultifields))))))
        .properties(
            OUTPUTS,
            Property.of(
                p ->
                    p.nested(
                        k ->
                            k.properties(VARIABLE_ID, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    VARIABLE_CLAUSE_ID, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    VARIABLE_CLAUSE_NAME, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    VARIABLE_VALUE_TYPE, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    OUTPUT_VARIABLE_RULE_ID,
                                    Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    OUTPUT_VARIABLE_RULE_ORDER,
                                    Property.of(p1 -> p1.long_(k1 -> k1)))
                                .properties(
                                    OUTPUT_VARIABLE_NAME, Property.of(p1 -> p1.keyword(k1 -> k1)))
                                .properties(
                                    VARIABLE_VALUE,
                                    Property.of(p1 -> p1.keyword(this::addValueMultifields))))))
        .properties(MATCHED_RULES, Property.of(p -> p.keyword(k -> k)))
        .properties(ROOT_DECISION_INSTANCE_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(ENGINE, Property.of(p -> p.keyword(k -> k)))
        .properties(TENANT_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(COLLECT_RESULT_VALUE, Property.of(p -> p.double_(k -> k)));
  }
}
