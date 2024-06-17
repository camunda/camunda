/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CamundaPlatformCondition.class)
@Component
public class PlatformAlertRecipientValidator implements AlertRecipientValidator {

  @Override
  public void validateAlertRecipientEmailAddresses(final List<String> emailAddresses) {
    if (emailAddresses.isEmpty()) {
      throw new OptimizeValidationException(
          "The field [emails] is not allowed to be empty. At least one recipient must be set.");
    }
  }
}
