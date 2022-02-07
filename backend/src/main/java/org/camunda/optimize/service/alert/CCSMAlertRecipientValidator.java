/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
    throw new OptimizeValidationException("Alerts are not available in CCSM");
  }

}
