/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;

import java.util.Arrays;

import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InstanceIndexUtil {

  public static String[] getDecisionInstanceIndexAliasName(final DecisionReportDataDto reportDataDto) {
    // for decision reports only one (the first) definition is supported
    return reportDataDto.getDefinitions().stream().findFirst()
      .map(ReportDataDefinitionDto::getKey)
      .map(InstanceIndexUtil::getDecisionInstanceIndexAliasName)
      .map(value -> new String[]{value})
      .orElse(new String[]{DECISION_INSTANCE_MULTI_ALIAS});
  }

  public static String getDecisionInstanceIndexAliasName(final String decisionDefinitionKey) {
    if (decisionDefinitionKey == null) {
      return DECISION_INSTANCE_MULTI_ALIAS;
    } else {
      return new DecisionInstanceIndex(decisionDefinitionKey).getIndexName();
    }
  }

  public static String[] getProcessInstanceIndexAliasNames(final ProcessReportDataDto reportDataDto) {
    return !reportDataDto.getDefinitions().isEmpty()
      ? reportDataDto.getDefinitions().stream()
      .map(ReportDataDefinitionDto::getKey)
      .map(InstanceIndexUtil::getProcessInstanceIndexAliasName)
      .toArray(String[]::new)
      : new String[]{PROCESS_INSTANCE_MULTI_ALIAS};
  }

  public static String getProcessInstanceIndexAliasName(final String processDefinitionKey) {
    if (processDefinitionKey == null) {
      return PROCESS_INSTANCE_MULTI_ALIAS;
    } else {
      return new ProcessInstanceIndex(processDefinitionKey).getIndexName();
    }
  }

  public static boolean isInstanceIndexNotFoundException(final ElasticsearchStatusException e) {
    return Arrays.stream(e.getSuppressed())
      .map(Throwable::getMessage)
      .anyMatch(msg -> msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE)
        && (containsInstanceIndexAliasOrPrefix(PROCESS, msg) || containsInstanceIndexAliasOrPrefix(DECISION, msg)));
  }

  public static boolean isInstanceIndexNotFoundException(final DefinitionType type,
                                                         final ElasticsearchStatusException e) {
    return Arrays.stream(e.getSuppressed())
      .map(Throwable::getMessage)
      .anyMatch(msg -> msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE) && containsInstanceIndexAliasOrPrefix(type, msg));
  }

  private static boolean containsInstanceIndexAliasOrPrefix(final DefinitionType type,
                                                            final String message) {
    switch (type) {
      case PROCESS:
        return message.contains(PROCESS_INSTANCE_INDEX_PREFIX) || message.contains(PROCESS_INSTANCE_MULTI_ALIAS);
      case DECISION:
        return message.contains(DECISION_INSTANCE_INDEX_PREFIX) || message.contains(DECISION_INSTANCE_MULTI_ALIAS);
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }
}
