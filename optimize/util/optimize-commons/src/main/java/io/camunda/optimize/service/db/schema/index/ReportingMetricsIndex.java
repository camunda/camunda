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
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

/**
 * Index mapping for {@code optimize-reporting-metrics}. One document per process instance,
 * aggregated from {@code REPORTING_PROCESS_*} Zeebe variable records.
 *
 * <p>Field types mirror the Python ingestion script in {@code poc-scripts/ingestion.py}, which is
 * the authoritative schema definition for this index.
 */
public abstract class ReportingMetricsIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  // Identity / routing fields
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String TENANT_ID = "tenantId";
  public static final String PROCESS_LABEL = "processLabel";
  public static final String STATE = "state";

  // Timestamp fields — stored as epoch-millisecond longs, formatted with "epoch_millis"
  public static final String FIRST_SEEN_AT = "firstSeenAt";
  public static final String LAST_SEEN_AT = "lastSeenAt";

  // Date string fields written by BPMN processes
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";

  // Cost / value metrics
  public static final String BASELINE_COST = "baselineCost";
  public static final String LLM_COST = "llmCost";
  public static final String AUTOMATION_COST = "automationCost";
  public static final String TOTAL_COST = "totalCost";
  public static final String VALUE_CREATED = "valueCreated";
  public static final String EXTERNAL_SERVICE_COST_USD = "externalServiceCostUsd";

  // Task-count metrics
  public static final String AGENT_TASK_COUNT = "agentTaskCount";
  public static final String HUMAN_TASK_COUNT = "humanTaskCount";
  public static final String AUTO_TASK_COUNT = "autoTaskCount";
  public static final String TOKEN_USAGE = "tokenUsage";
  public static final String ERROR_COUNT = "errorCount";
  public static final String RETRY_COUNT = "retryCount";
  public static final String API_CALL_COUNT = "apiCallCount";
  public static final String COMPLIANCE_CHECKS_PASSED = "complianceChecksPassed";

  // Timing metrics
  public static final String PROCESSING_TIME_MS = "processingTimeMs";
  public static final String QUEUE_WAIT_TIME_MS = "queueWaitTimeMs";

  // Environmental / quality metrics
  public static final String DATA_VOLUME_MB = "dataVolumeMb";
  public static final String CONFIDENCE_SCORE = "confidenceScore";
  public static final String CO2_EMISSIONS_KG = "co2EmissionsKg";
  public static final String CUSTOMER_SATISFACTION_SCORE = "customerSatisfactionScore";
  public static final String FRAUD_RISK_SCORE = "fraudRiskScore";

  // Boolean flags
  public static final String SLA_BREACHED = "slaBreached";
  public static final String ESCALATED = "escalated";
  public static final String MANUAL_OVERRIDE = "manualOverride";

  @Override
  public String getIndexName() {
    return DatabaseConstants.REPORTING_METRICS_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        // Identity / routing
        .properties(PROCESS_INSTANCE_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_LABEL, p -> p.keyword(k -> k))
        .properties(STATE, p -> p.keyword(k -> k))
        // Timestamps: epoch-ms longs from Zeebe
        .properties(FIRST_SEEN_AT, p -> p.date(d -> d.format("epoch_millis")))
        .properties(LAST_SEEN_AT, p -> p.date(d -> d.format("epoch_millis")))
        // Date strings set by BPMN processes
        .properties(START_DATE, p -> p.date(d -> d))
        .properties(END_DATE, p -> p.date(d -> d))
        // Cost / value metrics
        .properties(BASELINE_COST, p -> p.double_(d -> d))
        .properties(LLM_COST, p -> p.double_(d -> d))
        .properties(AUTOMATION_COST, p -> p.double_(d -> d))
        .properties(TOTAL_COST, p -> p.double_(d -> d))
        .properties(VALUE_CREATED, p -> p.double_(d -> d))
        .properties(EXTERNAL_SERVICE_COST_USD, p -> p.double_(d -> d))
        // Task-count / quality metrics
        .properties(AGENT_TASK_COUNT, p -> p.integer(i -> i))
        .properties(HUMAN_TASK_COUNT, p -> p.integer(i -> i))
        .properties(AUTO_TASK_COUNT, p -> p.integer(i -> i))
        .properties(TOKEN_USAGE, p -> p.long_(l -> l))
        .properties(ERROR_COUNT, p -> p.integer(i -> i))
        .properties(RETRY_COUNT, p -> p.integer(i -> i))
        .properties(API_CALL_COUNT, p -> p.integer(i -> i))
        .properties(COMPLIANCE_CHECKS_PASSED, p -> p.integer(i -> i))
        // Timing
        .properties(PROCESSING_TIME_MS, p -> p.integer(i -> i))
        .properties(QUEUE_WAIT_TIME_MS, p -> p.integer(i -> i))
        // Environmental / quality
        .properties(DATA_VOLUME_MB, p -> p.double_(d -> d))
        .properties(CONFIDENCE_SCORE, p -> p.double_(d -> d))
        .properties(CO2_EMISSIONS_KG, p -> p.double_(d -> d))
        .properties(CUSTOMER_SATISFACTION_SCORE, p -> p.double_(d -> d))
        .properties(FRAUD_RISK_SCORE, p -> p.double_(d -> d))
        // Boolean flags
        .properties(SLA_BREACHED, p -> p.boolean_(b -> b))
        .properties(ESCALATED, p -> p.boolean_(b -> b))
        .properties(MANUAL_OVERRIDE, p -> p.boolean_(b -> b));
  }
}
