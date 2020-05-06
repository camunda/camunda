/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.EmailSecurityProtocol;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class EmailNotificationService implements NotificationService {

  private final ConfigurationService configurationService;

  @Override
  public void notifyRecipient(String text, String destination) {
    if (configurationService.getEmailEnabled()
      && StringUtils.isNotEmpty(destination)) {
      try {
        log.debug("Sending email [{}] to [{}]", text, destination);
        sendEmail(destination, text);
      } catch (EmailException e) {
        log.error(
          "Was not able to send email from [{}] to [{}]!",
          configurationService.getAlertEmailAddress(),
          destination,
          e
        );
      }
    } else {
      if (StringUtils.isEmpty(destination)) {
        log.debug(
          "There is no email destination specified in the alert, therefore not sending any email notifications.");
      } else if (!configurationService.getEmailEnabled()) {
        log.warn(
          "There is an email destination specified in the alert but the email service is not enabled and thus no " +
            "email could be sent. Please check the Optimize documentation on how to enable email notifications!");
      }
    }
  }

  private void sendEmail(String to, String body) throws EmailException {
    Email email = new SimpleEmail();
    email.setHostName(configurationService.getAlertEmailHostname());
    email.setSmtpPort(configurationService.getAlertEmailPort());
    EmailAuthenticationConfiguration emailAuthenticationConfiguration =
      configurationService.getEmailAuthenticationConfiguration();
    if (emailAuthenticationConfiguration.getEnabled()) {
      email.setAuthentication(
        emailAuthenticationConfiguration.getUsername(),
        emailAuthenticationConfiguration.getPassword()
      );
      EmailSecurityProtocol securityProtocol = emailAuthenticationConfiguration.getSecurityProtocol();
      if (securityProtocol.equals(EmailSecurityProtocol.STARTTLS)) {
        email.setStartTLSEnabled(true);
      } else if (securityProtocol.equals(EmailSecurityProtocol.SSL_TLS)) {
        email.setSSLOnConnect(true);
        email.setSslSmtpPort(configurationService.getAlertEmailPort().toString());
      }
    }
    email.setFrom(configurationService.getAlertEmailAddress());

    email.setCharset("utf-8");
    email.setSubject("[Camunda-Optimize] - Report status");
    email.setMsg(body);
    email.addTo(to);
    email.send();
  }
}
