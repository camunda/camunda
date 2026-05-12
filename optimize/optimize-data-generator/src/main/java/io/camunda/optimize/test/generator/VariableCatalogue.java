/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates variable name→value pairs for one synthetic process instance.
 *
 * <p>Three categories are emitted:
 *
 * <ul>
 *   <li>Business variables — stable string sentinels keyed by instance key
 *   <li>Core {@code REPORTING_PROCESS_*} metrics — always present, randomly valued
 *   <li>Optional {@code REPORTING_PROCESS_*} metrics — each included with 60 % probability
 * </ul>
 *
 * <p>This class owns only the variable catalogue and value generation. How those values are
 * packaged as Zeebe records is the responsibility of {@link ZeebeRecordFactory}.
 */
class VariableCatalogue {

  private static final List<String> BUSINESS_VARS =
      List.of("customerId", "orderId", "amount", "status", "requiresReview", "priority");

  private enum VarType {
    INT,
    DOUBLE,
    BOOLEAN
  }

  private record OptionalVar(String name, VarType type) {}

  private static final List<OptionalVar> OPTIONAL_VARS =
      List.of(
          new OptionalVar("REPORTING_PROCESS_errorCount", VarType.INT),
          new OptionalVar("REPORTING_PROCESS_retryCount", VarType.INT),
          new OptionalVar("REPORTING_PROCESS_processingTimeMs", VarType.INT),
          new OptionalVar("REPORTING_PROCESS_queueWaitTimeMs", VarType.INT),
          new OptionalVar("REPORTING_PROCESS_apiCallCount", VarType.INT),
          new OptionalVar("REPORTING_PROCESS_complianceChecksPassed", VarType.INT),
          new OptionalVar("REPORTING_PROCESS_dataVolumeMb", VarType.DOUBLE),
          new OptionalVar("REPORTING_PROCESS_confidenceScore", VarType.DOUBLE),
          new OptionalVar("REPORTING_PROCESS_co2EmissionsKg", VarType.DOUBLE),
          new OptionalVar("REPORTING_PROCESS_customerSatisfactionScore", VarType.DOUBLE),
          new OptionalVar("REPORTING_PROCESS_fraudRiskScore", VarType.DOUBLE),
          new OptionalVar("REPORTING_PROCESS_externalServiceCostUsd", VarType.DOUBLE),
          new OptionalVar("REPORTING_PROCESS_slaBreached", VarType.BOOLEAN),
          new OptionalVar("REPORTING_PROCESS_escalated", VarType.BOOLEAN),
          new OptionalVar("REPORTING_PROCESS_manualOverride", VarType.BOOLEAN));

  private final Random rng;

  VariableCatalogue(final Random rng) {
    this.rng = rng;
  }

  /**
   * Returns all variable name→value pairs for one process instance. The caller is responsible for
   * wrapping each entry as a Zeebe variable record.
   */
  List<Map.Entry<String, String>> generate(final long instanceKey) {
    final List<Map.Entry<String, String>> vars = new ArrayList<>();
    vars.addAll(businessVars(instanceKey));
    vars.addAll(coreReportingVars());
    vars.addAll(optionalReportingVars());
    return vars;
  }

  private List<Map.Entry<String, String>> businessVars(final long instanceKey) {
    return IntStream.range(0, BUSINESS_VARS.size())
        .mapToObj(i -> Map.entry(BUSINESS_VARS.get(i), "\"synth-" + instanceKey + "-" + i + "\""))
        .collect(Collectors.toList());
  }

  private List<Map.Entry<String, String>> coreReportingVars() {
    final double baselineCost = round2(400 + rng.nextDouble() * 1_600);
    final double llmCost = round2(20 + rng.nextDouble() * 280);
    final double automationCost = round2(50 + rng.nextDouble() * 350);
    final double valueCreated = round2(baselineCost * (0.4 + rng.nextDouble() * 0.6));
    final long tokenUsage = 1_000L + (long) (rng.nextDouble() * 49_000);

    return List.of(
        Map.entry("REPORTING_PROCESS_baselineCost", String.valueOf(baselineCost)),
        Map.entry("REPORTING_PROCESS_llmCost", String.valueOf(llmCost)),
        Map.entry("REPORTING_PROCESS_automationCost", String.valueOf(automationCost)),
        Map.entry("REPORTING_PROCESS_totalCost", String.valueOf(round2(llmCost + automationCost))),
        Map.entry("REPORTING_PROCESS_valueCreated", String.valueOf(valueCreated)),
        Map.entry("REPORTING_PROCESS_agentTaskCount", String.valueOf(rng.nextInt(6))),
        Map.entry("REPORTING_PROCESS_humanTaskCount", String.valueOf(rng.nextInt(4))),
        Map.entry("REPORTING_PROCESS_autoTaskCount", String.valueOf(1 + rng.nextInt(6))),
        Map.entry("REPORTING_PROCESS_tokenUsage", String.valueOf(tokenUsage)));
  }

  /** Emits a random 60 %-probable subset of optional reporting variables. */
  private List<Map.Entry<String, String>> optionalReportingVars() {
    return OPTIONAL_VARS.stream()
        .filter(optVar -> rng.nextDouble() < 0.60)
        .map(optVar -> Map.entry(optVar.name(), randomValue(optVar.type())))
        .collect(Collectors.toList());
  }

  private String randomValue(final VarType type) {
    return switch (type) {
      case INT -> String.valueOf(rng.nextInt(1_000));
      case BOOLEAN -> String.valueOf(rng.nextBoolean());
      case DOUBLE -> String.valueOf(round2(rng.nextDouble() * 1_000));
    };
  }

  private static double round2(final double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
