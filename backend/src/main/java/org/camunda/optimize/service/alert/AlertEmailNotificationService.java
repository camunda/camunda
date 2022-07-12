/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import org.camunda.optimize.service.EmailSendingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
public class AlertEmailNotificationService implements AlertNotificationService {

  private final ConfigurationService configurationService;
  private final EmailSendingService emailSendingService;

  @Override
  public void notify(@NonNull final AlertNotificationDto notification) {
    notify(notification.getAlertMessage(), notification.getAlert().getEmails());
  }

  public void notify(String text, final List<String> recipients) {
    // This only works as the link is at the end of the composed text. We would need to refactor this if the email
    // structure of alerts changes in future
    String textWithTracking = text + "&utm_medium=email";
    recipients.forEach(recipient -> sendEmail(recipient, textWithTracking));
  }

  private void sendEmail(String to, String body) {
    emailSendingService.sendEmailWithErrorHandling(
      to,
      body,
      "[" + configurationService.getNotificationEmailCompanyBranding() + "-Optimize] - Report status"
    );
  }
}
