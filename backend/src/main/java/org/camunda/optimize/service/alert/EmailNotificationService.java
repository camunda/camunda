/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EmailNotificationService implements NotificationService {

  private final ConfigurationService configurationService;

  @Override
  public void notifyRecipient(String text, String destination) {
    log.debug("sending email [{}] to [{}]", text, destination);
    if (configurationService.getEmailEnabled()) {
      try {
        sendEmail(destination, text);
      } catch (EmailException e) {
        log.error("Was not able to send email from [{}] to [{}]!",
            configurationService.getAlertEmailAddress(),
            destination,
            e);
      }
    } else {
      log.warn("The email service is not enabled and thus no email could be send. " +
          "Please check the Optimize documentation on how to enable email notifications!");
    }
  }

  private void sendEmail(String to, String body) throws EmailException {

    Email email = new SimpleEmail();
    email.setHostName(configurationService.getAlertEmailHostname());
    email.setSmtpPort(configurationService.getAlertEmailPort());
    email.setAuthentication(
      configurationService.getAlertEmailUsername(),
      configurationService.getAlertEmailPassword()
    );
    email.setFrom(configurationService.getAlertEmailAddress());

    String securityProtocol = configurationService.getAlertEmailProtocol();
    if (securityProtocol.equals("STARTTLS")) {
      email.setStartTLSEnabled(true);
    } else if(securityProtocol.equals("SSL/TLS")) {
      email.setSSLOnConnect(true);
      email.setSslSmtpPort(configurationService.getAlertEmailPort().toString());
    }

    email.setCharset("utf-8");
    email.setSubject("[Camunda-Optimize] - Report status");
    email.setMsg(body);
    email.addTo(to);
    email.send();
  }
}
