/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Arrays;
import java.util.function.Function;
import org.elasticsearch.ElasticsearchStatusException;
import org.opensearch.client.opensearch._types.OpenSearchException;

public class InstanceIndexUtil {

  private InstanceIndexUtil() {}

  public static String[] getDecisionInstanceIndexAliasName(
      final DecisionReportDataDto reportDataDto) {
    // for decision reports only one (the first) definition is supported
    return reportDataDto.getDefinitions().stream()
        .findFirst()
        .map(ReportDataDefinitionDto::getKey)
        .map(InstanceIndexUtil::getDecisionInstanceIndexAliasName)
        .map(value -> new String[] {value})
        .orElse(new String[] {DECISION_INSTANCE_MULTI_ALIAS});
  }

  public static String getDecisionInstanceIndexAliasName(final String decisionDefinitionKey) {
    if (decisionDefinitionKey == null) {
      return DECISION_INSTANCE_MULTI_ALIAS;
    } else {
      return DecisionInstanceIndex.constructIndexName(decisionDefinitionKey);
    }
  }

  public static String[] getProcessInstanceIndexAliasNames(
      final ProcessReportDataDto reportDataDto) {
    if (reportDataDto.isManagementReport()) {
      return new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
    }
    return !reportDataDto.getDefinitions().isEmpty()
        ? reportDataDto.getDefinitions().stream()
            .map(ReportDataDefinitionDto::getKey)
            .map(InstanceIndexUtil::getProcessInstanceIndexAliasName)
            .toArray(String[]::new)
        : new String[] {PROCESS_INSTANCE_MULTI_ALIAS};
  }

  public static String getProcessInstanceIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return PROCESS_INSTANCE_MULTI_ALIAS;
    } else {
      return ProcessInstanceIndex.constructIndexName(processDefinitionKey);
    }
  }

  public static boolean isInstanceIndexNotFoundException(final RuntimeException e) {
    return isInstanceIndexNotFoundException(e, msg -> true);
  }

  public static boolean isInstanceIndexNotFoundException(
      final DefinitionType type, final RuntimeException e) {
    return isInstanceIndexNotFoundException(
        e, msg -> containsInstanceIndexAliasOrPrefix(type, e.getMessage()));
  }

  private static boolean isInstanceIndexNotFoundException(
      final RuntimeException e, final Function<String, Boolean> messageFilter) {
    if (e instanceof ElasticsearchStatusException) {
      return Arrays.stream(e.getSuppressed())
          .map(Throwable::getMessage)
          .anyMatch(
              msg -> msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE) && messageFilter.apply(msg));
    } else if (e instanceof OpenSearchException) {
      return e.getMessage().contains(INDEX_NOT_FOUND_EXCEPTION_TYPE)
          && messageFilter.apply(e.getMessage());
    } else {
      return false;
    }
  }

  private static boolean containsInstanceIndexAliasOrPrefix(
      final DefinitionType type, final String message) {
    switch (type) {
      case PROCESS:
        return message.contains(PROCESS_INSTANCE_INDEX_PREFIX)
            || message.contains(PROCESS_INSTANCE_MULTI_ALIAS);
      case DECISION:
        return message.contains(DECISION_INSTANCE_INDEX_PREFIX)
            || message.contains(DECISION_INSTANCE_MULTI_ALIAS);
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }
}
