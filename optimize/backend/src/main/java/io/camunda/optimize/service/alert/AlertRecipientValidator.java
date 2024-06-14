/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.alert;

import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.util.List;

public interface AlertRecipientValidator {

  void validateAlertRecipientEmailAddresses(List<String> emailAddresses)
      throws OptimizeValidationException;
}
