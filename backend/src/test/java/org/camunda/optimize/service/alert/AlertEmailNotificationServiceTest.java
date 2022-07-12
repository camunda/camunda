/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.DummySSLSocketFactory;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import org.camunda.optimize.service.EmailSendingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.EmailSecurityProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.security.Security;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.NONE;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.SSL_TLS;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.STARTTLS;

@ExtendWith(MockitoExtension.class)
public class AlertEmailNotificationServiceTest {

  private ConfigurationService configurationService;

  private AlertEmailNotificationService notificationService;
  private EmailSendingService emailSendingService;

  private GreenMail greenMail;

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createConfiguration()
      .loadConfigurationFrom("service-config.yaml")
      .build();
    configurationService.setEmailEnabled(true);
    configurationService.setNotificationEmailAddress("from@localhost.com");
    configurationService.setNotificationEmailHostname("127.0.0.1");
    configurationService.setNotificationEmailPort(4444);
    this.emailSendingService = new EmailSendingService(configurationService);
    this.notificationService = new AlertEmailNotificationService(configurationService, emailSendingService);
  }

  @AfterEach
  public void cleanUp() {
    if (greenMail != null) {
      greenMail.stop();
    }
  }

  private void initGreenMail(String protocol) {
    greenMail = new GreenMail(new ServerSetup(4444, null, protocol));
    greenMail.start();
    greenMail.setUser("from@localhost.com", "demo", "demo");
  }

  @ParameterizedTest(name = "test send email with security protocol = {0}")
  @MethodSource("getSecurityProtocolVariations")
  public void sendEmailWithSecurityProtocolVariations(EmailSecurityProtocol emailSecurityProtocol) {
    // given
    mockConfig(true, "demo", "demo", emailSecurityProtocol);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify("some body text", singletonList("to@localhost.com"));

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
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
    notificationService.notify("some body text", singletonList("to@localhost.com"));

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    assertThat(GreenMailUtil.getBody(emails[0])).isEqualTo("some body text&utm_medium=email");
  }

  @Test
  public void sendEmailWithoutAuthenticationEnabled() {
    // given
    mockConfig(false, null, null, NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify("some body text", singletonList("to@localhost.com"));

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
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
    notificationService.notify("some body text", singletonList("to@localhost.com"));

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).isEmpty();
  }

  @Test
  public void sendEmailToMultipleRecipients() throws MessagingException {
    // given
    mockConfig(true, "demo", "demo", NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify("some body text", newArrayList("to1@localhost.com", "to2@localhost.com"));

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(2);
    assertThat(emails[0].getRecipients(Message.RecipientType.TO)[0]).hasToString("to1@localhost.com");
    assertThat(emails[1].getRecipients(Message.RecipientType.TO)[0]).hasToString("to2@localhost.com");
  }

  @Test
  public void sendEmailToRemainingRecipientsIfOneFails() throws MessagingException {
    // given
    mockConfig(true, "demo", "demo", NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify(
      "some body text",
      newArrayList("invalidAddressThatThrowsError", "to2@localhost.com")
    );

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).hasSize(1);
    assertThat(emails[0].getRecipients(Message.RecipientType.TO)[0]).hasToString("to2@localhost.com");
  }

  @Test
  public void notifyRecipientsFails() {
    // given
    mockConfig(true, "demo", "demo", NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notify(
      "some body text",
      newArrayList("invalidAddressThatThrowsError")
    );

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails).isEmpty();
  }

  private static Stream<EmailSecurityProtocol> getSecurityProtocolVariations() {
    return Stream.of(NONE, STARTTLS);
  }

  private void mockConfig(boolean authenticationEnabled, String username, String password,
                          EmailSecurityProtocol securityProtocol) {
    EmailAuthenticationConfiguration emailAuthenticationConfiguration =
      configurationService.getEmailAuthenticationConfiguration();
    emailAuthenticationConfiguration.setEnabled(authenticationEnabled);
    emailAuthenticationConfiguration.setUsername(username);
    emailAuthenticationConfiguration.setPassword(password);
    emailAuthenticationConfiguration.setSecurityProtocol(securityProtocol);
  }

}
