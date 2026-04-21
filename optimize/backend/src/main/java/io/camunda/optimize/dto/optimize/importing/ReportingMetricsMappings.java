/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import static java.util.Map.entry;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared mapping and script metadata for reporting-metrics import/upsert. */
public final class ReportingMetricsMappings {

  public static final String REPORTING_PREFIX = "REPORTING_PROCESS_";

  private static final Logger LOG = LoggerFactory.getLogger(ReportingMetricsMappings.class);

  private static final List<String> UPSERT_FIELDS =
      List.of(
          "agentTaskCount",
          "apiCallCount",
          "autoTaskCount",
          "automationCost",
          "baselineCost",
          "co2EmissionsKg",
          "complianceChecksPassed",
          "confidenceScore",
          "customerSatisfactionScore",
          "dataVolumeMb",
          "endDate",
          "errorCount",
          "escalated",
          "externalServiceCostUsd",
          "firstSeenAt",
          "fraudRiskScore",
          "humanTaskCount",
          "lastSeenAt",
          "llmCost",
          "manualOverride",
          "processDefinitionKey",
          "processInstanceKey",
          "processLabel",
          "processingTimeMs",
          "queueWaitTimeMs",
          "retryCount",
          "slaBreached",
          "tenantId",
          "tokenUsage",
          "totalCost",
          "valueCreated");

  private static final String UPDATE_SCRIPT =
      UPSERT_FIELDS.stream()
          .sorted()
          .map(
              field ->
                  "if (params.containsKey('"
                      + field
                      + "')) { ctx._source."
                      + field
                      + " = params."
                      + field
                      + "; }\n")
          .collect(Collectors.joining());

  private static final Map<String, BiConsumer<ReportingMetricsDto, String>> VARIABLE_APPLIERS =
      Map.ofEntries(
          entry(
              "REPORTING_PROCESS_baselineCost",
              (doc, raw) -> doc.setBaselineCost(parseDouble(raw))),
          entry("REPORTING_PROCESS_llmCost", (doc, raw) -> doc.setLlmCost(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_automationCost",
              (doc, raw) -> doc.setAutomationCost(parseDouble(raw))),
          entry("REPORTING_PROCESS_totalCost", (doc, raw) -> doc.setTotalCost(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_valueCreated",
              (doc, raw) -> doc.setValueCreated(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_agentTaskCount",
              (doc, raw) -> doc.setAgentTaskCount(parseInt(raw))),
          entry(
              "REPORTING_PROCESS_humanTaskCount",
              (doc, raw) -> doc.setHumanTaskCount(parseInt(raw))),
          entry(
              "REPORTING_PROCESS_autoTaskCount", (doc, raw) -> doc.setAutoTaskCount(parseInt(raw))),
          entry("REPORTING_PROCESS_tokenUsage", (doc, raw) -> doc.setTokenUsage(parseLong(raw))),
          entry("REPORTING_PROCESS_processLabel", (doc, raw) -> doc.setProcessLabel(unquote(raw))),
          entry("REPORTING_PROCESS_startDate", (doc, raw) -> doc.setStartDate(unquote(raw))),
          entry("REPORTING_PROCESS_endDate", (doc, raw) -> doc.setEndDate(unquote(raw))),
          entry("REPORTING_PROCESS_errorCount", (doc, raw) -> doc.setErrorCount(parseInt(raw))),
          entry("REPORTING_PROCESS_retryCount", (doc, raw) -> doc.setRetryCount(parseInt(raw))),
          entry(
              "REPORTING_PROCESS_processingTimeMs",
              (doc, raw) -> doc.setProcessingTimeMs(parseInt(raw))),
          entry(
              "REPORTING_PROCESS_queueWaitTimeMs",
              (doc, raw) -> doc.setQueueWaitTimeMs(parseInt(raw))),
          entry("REPORTING_PROCESS_apiCallCount", (doc, raw) -> doc.setApiCallCount(parseInt(raw))),
          entry(
              "REPORTING_PROCESS_complianceChecksPassed",
              (doc, raw) -> doc.setComplianceChecksPassed(parseInt(raw))),
          entry(
              "REPORTING_PROCESS_dataVolumeMb",
              (doc, raw) -> doc.setDataVolumeMb(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_confidenceScore",
              (doc, raw) -> doc.setConfidenceScore(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_co2EmissionsKg",
              (doc, raw) -> doc.setCo2EmissionsKg(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_customerSatisfactionScore",
              (doc, raw) -> doc.setCustomerSatisfactionScore(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_fraudRiskScore",
              (doc, raw) -> doc.setFraudRiskScore(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_externalServiceCostUsd",
              (doc, raw) -> doc.setExternalServiceCostUsd(parseDouble(raw))),
          entry(
              "REPORTING_PROCESS_slaBreached", (doc, raw) -> doc.setSlaBreached(parseBoolean(raw))),
          entry("REPORTING_PROCESS_escalated", (doc, raw) -> doc.setEscalated(parseBoolean(raw))),
          entry(
              "REPORTING_PROCESS_manualOverride",
              (doc, raw) -> doc.setManualOverride(parseBoolean(raw))));

  private ReportingMetricsMappings() {}

  public static String getUpdateScript() {
    return UPDATE_SCRIPT;
  }

  public static boolean applyVariable(
      final ReportingMetricsDto doc, final String variableName, final String rawValue) {
    final BiConsumer<ReportingMetricsDto, String> applier = VARIABLE_APPLIERS.get(variableName);
    if (applier == null) {
      return false;
    }
    applier.accept(doc, rawValue);
    return true;
  }

  private static Double parseDouble(final String raw) {
    try {
      return Double.parseDouble(raw.trim());
    } catch (final RuntimeException e) {
      LOG.debug("Could not parse double from value: {}", raw);
      return null;
    }
  }

  private static Integer parseInt(final String raw) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (final RuntimeException e) {
      LOG.debug("Could not parse int from value: {}", raw);
      return null;
    }
  }

  private static Long parseLong(final String raw) {
    try {
      return Long.parseLong(raw.trim());
    } catch (final RuntimeException e) {
      LOG.debug("Could not parse long from value: {}", raw);
      return null;
    }
  }

  private static Boolean parseBoolean(final String raw) {
    final String value = unquote(raw).trim();
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    LOG.debug("Could not parse boolean from value: {}", raw);
    return null;
  }

  private static String unquote(final String raw) {
    final String value = raw.trim();
    if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}
