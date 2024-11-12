/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.script;

public interface ProcessOverviewScriptFactory {
  static String createUpdateOverviewScript() {
    return """
        ctx._source.owner = params.owner;
        ctx._source.processDefinitionKey = params.processDefinitionKey;
        ctx._source.digest.enabled = params.digestEnabled;
        if (params.digestCheckIntervalValue != null && params.digestCheckIntervalUnit != null) {
          def alertInterval = [
            'value': params.digestCheckIntervalValue,
            'unit': params.digestCheckIntervalUnit
          ];
          ctx._source.digest.checkInterval = alertInterval;
        }
        """;
  }

  static String createUpdateProcessDigestScript() {
    return "ctx._source.digest.kpiReportResults = params.kpiReportResults;";
  }

  static String createUpdateOwnerIfNotSetScript() {
    return """
        if (ctx._source.owner == null) {
          ctx._source.owner = params.owner;
        }
        ctx._source.processDefinitionKey = params.processDefinitionKey;
        """;
  }

  static String createUpdateKpisScript() {
    return "ctx._source.lastKpiEvaluationResults = params.lastKpiEvaluationResults;";
  }
}
