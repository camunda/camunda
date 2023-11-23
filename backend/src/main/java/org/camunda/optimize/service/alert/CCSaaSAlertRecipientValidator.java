/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import org.camunda.optimize.service.identity.CCSaaSIdentityService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Conditional(CCSaaSCondition.class)
@Component
@RequiredArgsConstructor
public class CCSaaSAlertRecipientValidator implements AlertRecipientValidator {

  private final CCSaaSIdentityService identityService;

  @Override
  public void validateAlertRecipientEmailAddresses(final List<String> emails) {
    final List<String> lowerCasedUserEmails = identityService.getUsersByEmail(emails)
      .stream().map(user -> user.getEmail().toLowerCase()).toList();
    final List<String> lowerCasedInputEmails = emails.stream().map(String::toLowerCase).toList();
    final Collection<String> unknownEmails = CollectionUtils.subtract(lowerCasedInputEmails, lowerCasedUserEmails);
    if (!unknownEmails.isEmpty()) {
      throw new OptimizeAlertEmailValidationException(new HashSet<>(unknownEmails));
    }
  }

}
