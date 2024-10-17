/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import io.camunda.optimize.service.identity.CCSaaSIdentityService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CCSaaSCondition.class)
@Component
public class CCSaaSAlertRecipientValidator implements AlertRecipientValidator {

  private final CCSaaSIdentityService identityService;

  public CCSaaSAlertRecipientValidator(final CCSaaSIdentityService identityService) {
    this.identityService = identityService;
  }

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
