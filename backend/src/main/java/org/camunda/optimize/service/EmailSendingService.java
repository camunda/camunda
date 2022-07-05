/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;


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
public class EmailSendingService {
  private final ConfigurationService configurationService;

  public void sendEmailWithErrorHandling(final String recipient, final String body, final String subject) {
    if (configurationService.getEmailEnabled()) {
      if (StringUtils.isNotEmpty(recipient)) {
        try {
          log.debug("Sending email [{}] to [{}]", body, recipient);
          sendEmail(recipient, subject, body);
        } catch (EmailException e) {
          log.error(
            "Was not able to send email from [{}] to [{}]!",
            configurationService.getNotificationEmailAddress(),
            recipient,
            e
          );
        }
      } else {
        log.debug(
          "There is no email destination specified, therefore not sending any email notifications.");
      }
    } else if (StringUtils.isNotEmpty(recipient)) {
      log.warn(
        "The email service is not enabled, so no email will be sent. Please check the Optimize documentation on how to enable " +
          "email notifications!");
    }
  }

  private void sendEmail(final String recipient, final String subject, final String body) throws EmailException {
    Email email = new SimpleEmail();
    email.setHostName(configurationService.getNotificationEmailHostname());
    email.setSmtpPort(configurationService.getNotificationEmailPort());
    final EmailAuthenticationConfiguration emailAuthenticationConfiguration =
      configurationService.getEmailAuthenticationConfiguration();
    if (Boolean.TRUE.equals(emailAuthenticationConfiguration.getEnabled())) {
      email.setAuthentication(
        emailAuthenticationConfiguration.getUsername(),
        emailAuthenticationConfiguration.getPassword()
      );
      EmailSecurityProtocol securityProtocol = emailAuthenticationConfiguration.getSecurityProtocol();
      if (securityProtocol.equals(EmailSecurityProtocol.STARTTLS)) {
        email.setStartTLSEnabled(true);
      } else if (securityProtocol.equals(EmailSecurityProtocol.SSL_TLS)) {
        email.setSSLOnConnect(true);
        email.setSslSmtpPort(configurationService.getNotificationEmailPort().toString());
      }
    }
    email.setFrom(configurationService.getNotificationEmailAddress());
    email.setCharset("utf-8");
    email.setSubject(subject);
    email.setMsg(body);
    email.addTo(recipient);
    email.send();
  }
}
