/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.DummySSLSocketFactory;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
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

import javax.mail.internet.MimeMessage;
import java.security.Security;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.NONE;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.SSL_TLS;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.STARTTLS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class EmailNotificationServiceTest {

  private ConfigurationService configurationService;

  private EmailNotificationService notificationService;

  private GreenMail greenMail;

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createConfiguration()
      .loadConfigurationFrom("service-config.yaml")
      .build();
    configurationService.setEmailEnabled(true);
    configurationService.setAlertEmailAddress("from@localhost.com");
    configurationService.setAlertEmailHostname("127.0.0.1");
    configurationService.setAlertEmailPort(4444);
    this.notificationService = new EmailNotificationService(configurationService);
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
    notificationService.notifyRecipient("some body text", "to@localhost.com");

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(GreenMailUtil.getBody(emails[0]), is("some body text"));
  }

  @Test
  public void sendEmailWithSSLTLSProtocol() {
    // given
    Security.setProperty("ssl.SocketFactory.provider", DummySSLSocketFactory.class.getName());
    mockConfig(true, "demo", "demo", SSL_TLS);
    initGreenMail(ServerSetup.PROTOCOL_SMTPS);

    // when
    notificationService.notifyRecipient("some body text", "to@localhost.com");

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(GreenMailUtil.getBody(emails[0]), is("some body text"));
  }

  @Test
  public void sendEmailWithoutAuthenticationEnabled() {
    // given
    mockConfig(false, null, null, NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notifyRecipient("some body text", "to@localhost.com");

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(GreenMailUtil.getBody(emails[0]), is("some body text"));
  }

  @Test
  public void notifyRecipientWithEmailDisabledDoesNotSendEmail() {
    // given
    configurationService.setEmailEnabled(false);
    mockConfig(true, "demo", "demo", NONE);
    initGreenMail(ServerSetup.PROTOCOL_SMTPS);

    // when
    notificationService.notifyRecipient("some body text", "to@localhost.com");

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(0));
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
