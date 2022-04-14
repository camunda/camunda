/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Conditional(CCSMCondition.class)
@Component
@RequiredArgsConstructor
public class CCSMAlertRecipientValidator implements AlertRecipientValidator {

  @Override
  public List<String> getValidatedRecipientEmailList(final List<String> emails) {
    throw new OptimizeValidationException("Alerts are not available in Camunda Platform Self-Managed");
  }

}
