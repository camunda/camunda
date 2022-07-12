/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.onboardinglistener;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.EmailSendingService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class OnboardingNotificationService {

  public static final String MAGIC_LINK_TEMPLATE = "/collection/%s/dashboard/%s/";
  public static final String EMAIL_BODY_TEMPLATE =
    "Congratulations! Your first process instance for the process %s has completed! " +
      "Ready for some insights? Our systems have crunched the data and we have some valuable information to " +
      "share with you. Curious? Please follow this link to learn more: %s";
  public static final String EMAIL_SUBJECT = "You've got insights from Optimize for your new process";

  private EmailSendingService emailSendingService;

  public void notifyOnboarding(@NonNull final String processKey) {
    notifyOnboarding(processKey, retrieveEmailRecipient(processKey));
  }

  private String retrieveEmailRecipient(@NonNull final String processKey) {
    // For now just a dummy, will be implemented with OPT-6189. Make sure to adjust the test case
    // emailNotificationIsSentCorrectly accordingly.
    return "to@localhost.com";
  }

  public void notifyOnboarding(@NonNull final String processKey, @NonNull final String emailRecipient) {
    emailSendingService.sendEmailWithErrorHandling(
      emailRecipient,
      createEmailText(processKey),
      EMAIL_SUBJECT
    );
  }

  private String createEmailText(final String processKey) {
    String magicLink = generateMagicLinkForProcess(processKey);
    return String.format(EMAIL_BODY_TEMPLATE, processKey, magicLink);
  }

  private String generateMagicLinkForProcess(final String processKey) {
    return String.format(MAGIC_LINK_TEMPLATE, processKey, processKey);
  }
}
