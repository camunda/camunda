/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.email;


import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.EmailSecurityProtocol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

@AllArgsConstructor
@Component
@Slf4j
public class EmailService {

  private final ConfigurationService configurationService;

  @Autowired
  private final FreeMarkerConfigurer freemarkerConfigurer;

  public void sendTemplatedEmailWithErrorHandling(final String recipient, final String subject, final String templateName,
                                                  final Map<String, Object> templateInput) {
    sendEmailWithErrorHandling(recipient, composeEmailContentFromTemplate(templateName, templateInput), subject, true);
  }

  // TODO To be removed with OPT-6381
  public void sendEmailWithErrorHandling(final String recipient, final String body, final String subject) {
    sendEmailWithErrorHandling(recipient, body, subject, false);
  }

  private void sendEmailWithErrorHandling(final String recipient, final String body, final String subject,
                                          final boolean fromTemplate) {
    if (configurationService.getEmailEnabled()) {
      if (StringUtils.isNotEmpty(recipient)) {
        try {
          log.debug("Sending email [{}] to [{}]", subject, recipient);
          if (fromTemplate) {
            sendHtmlMessage(recipient, subject, body);
          } else {
            sendEmail(recipient, subject, body);
          }
        } catch (MessagingException e) {
          log.error(
            "Was not able to send email from [{}] to [{}]!",
            configurationService.getNotificationEmailAddress(),
            recipient,
            e
          );
        }
      } else {
        log.warn(
          "There is no email destination specified, therefore not sending any email notifications.");
      }
    } else if (StringUtils.isNotEmpty(recipient)) {
      log.warn(
        "The email service is not enabled, so no email will be sent. Please check the Optimize documentation on how to enable " +
          "email notifications!");
    }
  }

  // TODO To be removed with OPT-6381
  private void sendEmail(final String recipient, final String subject, final String body) throws MessagingException {
    MimeMessage message = createMimeMessage();
    message.setFrom(new InternetAddress(configurationService.getNotificationEmailAddress()));
    validateAddress(recipient);
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
    message.setSubject(subject);
    message.setText(body, "utf-8");

    Transport.send(message);
  }

  private static void validateAddress(final String recipient) throws AddressException {
    InternetAddress.parse(recipient)[0].validate();
  }

  private void sendHtmlMessage(final String recipient, final String subject, final String htmlBody) throws MessagingException {
    MimeMessage message = createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setTo(recipient);
    helper.setSubject(subject);
    helper.setText(htmlBody, true);
    helper.setFrom(configurationService.getNotificationEmailAddress());
    Transport.send(message);
  }

  private MimeMessage createMimeMessage() {
    Properties properties = new Properties();
    properties.setProperty("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.host", configurationService.getNotificationEmailHostname());
    properties.put("mail.smtp.port", configurationService.getNotificationEmailPort());

    final EmailAuthenticationConfiguration emailAuthenticationConfiguration =
      configurationService.getEmailAuthenticationConfiguration();
    Session session;
    if (Boolean.TRUE.equals(emailAuthenticationConfiguration.getEnabled())) {
      properties.put("mail.smtp.auth", "true");
      Authenticator auth = new Authenticator() {
        @Override
        public PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(
            emailAuthenticationConfiguration.getUsername(),
            emailAuthenticationConfiguration.getPassword()
          );
        }
      };
      EmailSecurityProtocol securityProtocol = emailAuthenticationConfiguration.getSecurityProtocol();
      if (securityProtocol.equals(EmailSecurityProtocol.STARTTLS)) {
        properties.put("mail.smtp.starttls.enable", "true");
      } else if (securityProtocol.equals(EmailSecurityProtocol.SSL_TLS)) {
        properties.setProperty("mail.smtp.port", configurationService.getNotificationEmailPort().toString());
        properties.setProperty("mail.smtp.socketFactory.port", configurationService.getNotificationEmailPort().toString());
        properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        properties.setProperty("mail.smtp.socketFactory.fallback", "false");
      }
      session = Session.getInstance(properties, auth);
    } else {
      properties.put("mail.smtp.auth", "false");
      session = Session.getInstance(properties, null);
    }

    return new MimeMessage(session);
  }

  private String composeEmailContentFromTemplate(final String templateName, final Map<String, Object> templateInput) {
    try {
      final Template freemarkerTemplate = freemarkerConfigurer.getConfiguration().getTemplate(templateName);
      return FreeMarkerTemplateUtils.processTemplateIntoString(freemarkerTemplate, templateInput);
    } catch (IOException e) {
      final String reason = String.format("Failed to read email template %s.", templateName);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (TemplateException e) {
      final String reason = String.format("Failed to process email template  %s.", templateName);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

}
