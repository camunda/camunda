/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import org.camunda.optimize.service.identity.CCSaaSIdentityService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Conditional(CCSaaSCondition.class)
@Component
@RequiredArgsConstructor
public class CCSaaSAlertRecipientValidator implements AlertRecipientValidator {

  private final CCSaaSIdentityService identityService;

  @Override
  public void validateAlertRecipientEmailAddresses(final List<String> emails) {
    final List<String> userEmails = identityService.getUsersByEmail(emails)
      .stream().map(UserDto::getEmail).collect(Collectors.toList());
    final Collection<String> unknownEmails = CollectionUtils.subtract(emails, userEmails);
    if (!unknownEmails.isEmpty()) {
      throw new OptimizeAlertEmailValidationException(new HashSet<>(unknownEmails));
    }
  }

}
