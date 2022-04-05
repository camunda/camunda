/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.dmn;

import io.camunda.operate.entities.FlowNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum DecisionType {
  DECISION_TABLE,

  UNSPECIFIED,
  UNKNOWN;

  private static final Logger logger = LoggerFactory.getLogger(FlowNodeType.class);

  public static DecisionType fromZeebeDecisionType(String decisionType) {
    if (decisionType == null) {
      return UNSPECIFIED;
    }
    try {
      return DecisionType.valueOf(decisionType);
    } catch (IllegalArgumentException ex) {
      logger.error("Decision type not found for value [{}]. UNKNOWN type will be assigned.", decisionType);
      return UNKNOWN;
    }
  }

}
