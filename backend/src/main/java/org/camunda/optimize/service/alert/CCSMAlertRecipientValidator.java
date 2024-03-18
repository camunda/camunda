/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import org.camunda.optimize.service.identity.CCSMIdentityService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CCSMCondition.class)
@Component
@RequiredArgsConstructor
public class CCSMAlertRecipientValidator implements AlertRecipientValidator {
  private final CCSMIdentityService identityService;

  @Override
  public void validateAlertRecipientEmailAddresses(final List<String> emails) {
    final Set<String> emailsForSearch =
        emails.stream()
            .filter(StringUtils::isNotBlank)
            .map(email -> email.toLowerCase(Locale.ENGLISH).trim())
            .collect(toSet());
    final Set<String> lowerCasedUserEmails =
        identityService.getUsersByEmail(emailsForSearch).stream()
            .map(user -> user.getEmail().toLowerCase(Locale.ENGLISH))
            .collect(toSet());
    final Collection<String> unknownEmails =
        CollectionUtils.subtract(emailsForSearch, lowerCasedUserEmails);
    if (!unknownEmails.isEmpty()) {
      throw new OptimizeAlertEmailValidationException(new HashSet<>(unknownEmails));
    }
  }
}
