/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import static io.camunda.optimize.service.util.configuration.EmailSecurityProtocol.NONE;
import static io.camunda.optimize.service.util.configuration.EmailSecurityProtocol.SSL_TLS;
import static io.camunda.optimize.service.util.configuration.EmailSecurityProtocol.STARTTLS;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.DummySSLSocketFactory;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import io.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import io.camunda.optimize.dto.optimize.alert.AlertNotificationType;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.service.email.EmailService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import io.camunda.optimize.service.util.configuration.EmailSecurityProtocol;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.security.Security;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

@ExtendWith(MockitoExtension.class)
public class AlertEmailNotificationServiceTest {

  private ConfigurationService configurationService;

  private AlertEmailNotificationService notificationService;
  @Autowired private FreeMarkerConfigurer freemarkerConfigurer;

  private GreenMail greenMail;

  @BeforeEach
  public void init() {
    configurationService =
        ConfigurationServiceBuilder.createConfiguration()
            .loadConfigurationFrom("service-config.yaml")
            .build();
    configurationService.setEmailEnabled(true);
    configurationService.setNotificationEmailAddress("from@localhost.com");
    configurationService.setNotificationEmailHostname("127.0.0.1");
    configurationService.setNotificationEmailPort(4444);
    final EmailService emailService = new EmailService(configurationService, freemarkerConfigurer);
    this.notificationService =
        new AlertEmailNotificationService(configurationService, emailService);
  }

  @AfterEach
  public void cleanUp() {
    if (greenMail != null) {
      greenMail.stop();
    }
  }

  private void initGreenMail(final String protocol) {
    greenMail = new GreenMail(new ServerSetup(4444, null, protocol));
    greenMail.start();
    greenMail.setUser("from@localhost.com", "demo", "demo");
  }

  @ParameterizedTest(name = "test send email with security protocol = {0}")
  @MethodSource("getSecurityProtocolVariations")
  public void sendEmailWithSecurityProtocolVariations(
      final EmailSecurityProtocol emailSecurityProtocol) {
    // given
    mockConfig(true, "demo", "demo", emailSecurityProtocol);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify(
        createEmailNotification("some body text", singletonList("to@localhost.com")));

    // then
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    assertThat(GreenMailUtil.getBody(emails[0])).isEqualTo("some body text&utm_medium=email");
  }

  @Test
  public void sendEmailWithSSLTLSProtocol() {
    // given
    Security.setProperty("ssl.SocketFactory.provider", DummySSLSocketFactory.class.getName());
    mockConfig(true, "demo", "demo", SSL_TLS);
    initGreenMail(ServerSetup.PROTOCOL_SMTPS);

    // when
    notificationService.notify(
        createEmailNotification("some body text", singletonList("to@localhost.com")));

    // then
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    assertThat(GreenMailUtil.getBody(emails[0])).isEqualTo("some body text&utm_medium=email");
  }

  @Test
  public void sendEmailWithoutAuthenticationEnabled() {
    // given
    mockConfig(false, null, null, NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify(
        createEmailNotification("some body text", singletonList("to@localhost.com")));

    // then
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    assertThat(GreenMailUtil.getBody(emails[0])).isEqualTo("some body text&utm_medium=email");
  }

  @Test
  public void notifyRecipientWithEmailDisabledDoesNotSendEmail() {
    // given
    configurationService.setEmailEnabled(false);
    mockConfig(true, "demo", "demo", NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTPS);

    // when
    notificationService.notify(
        createEmailNotification("some body text", singletonList("to@localhost.com")));

    // then
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).isEmpty();
  }

  @Test
  public void sendEmailToMultipleRecipients() throws MessagingException {
    // given
    mockConfig(true, "demo", "demo", NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify(
        createEmailNotification(
            "some body text", List.of("to1@localhost.com", "to2@localhost.com")));

    // then
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(2);
    assertThat(emails[0].getRecipients(Message.RecipientType.TO)[0])
        .hasToString("to1@localhost.com");
    assertThat(emails[1].getRecipients(Message.RecipientType.TO)[0])
        .hasToString("to2@localhost.com");
  }

  @Test
  public void sendEmailToRemainingRecipientsIfOneFails() throws MessagingException {
    // given
    mockConfig(true, "demo", "demo", NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify(
        createEmailNotification(
            "some body text", List.of("invalidAddressThatThrowsError", "to2@localhost.com")));

    // then
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    assertThat(emails[0].getRecipients(Message.RecipientType.TO)[0])
        .hasToString("to2@localhost.com");
  }

  @Test
  public void notifyRecipientsFails() {
    // given
    mockConfig(true, "demo", "demo", NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify(
        createEmailNotification("some body text", List.of("invalidAddressThatThrowsError")));

    // then
    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).isEmpty();
  }

  private static Stream<EmailSecurityProtocol> getSecurityProtocolVariations() {
    return Stream.of(NONE, STARTTLS);
  }

  private void mockConfig(
      final boolean authenticationEnabled,
      final String username,
      final String password,
      final EmailSecurityProtocol securityProtocol) {
    final EmailAuthenticationConfiguration emailAuthenticationConfiguration =
        configurationService.getEmailAuthenticationConfiguration();
    emailAuthenticationConfiguration.setEnabled(authenticationEnabled);
    emailAuthenticationConfiguration.setUsername(username);
    emailAuthenticationConfiguration.setPassword(password);
    emailAuthenticationConfiguration.setSecurityProtocol(securityProtocol);
  }

  private AlertNotificationDto createEmailNotification(
      final String text, final List<String> recipients) {
    final AlertDefinitionDto alertDefinitionDto = new AlertDefinitionDto();
    alertDefinitionDto.setEmails(recipients);
    return new AlertNotificationDto(
        alertDefinitionDto, 0., AlertNotificationType.NEW, text, "linkToReport");
  }
}
