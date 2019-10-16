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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.mail.internet.MimeMessage;
import java.security.Security;

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
    this.notificationService = new EmailNotificationService(configurationService);
  }

  @AfterEach
  public void cleanUp() {
    if (greenMail != null) {
      greenMail.stop();
    }
  }

  private void initGreenMail(int port, String protocol) {
    greenMail = new GreenMail(new ServerSetup(port, null, protocol));
    greenMail.start();
    greenMail.setUser("from@localhost.com", "demo", "demo");
    greenMail.setUser("to@localhost.com", "demo", "demo");
  }

  @Test
  public void sendEmailWithoutSecureConnection() {

    // given
    mockConfig("demo", "demo", "from@localhost.com", "127.0.0.1", 6666, "NONE");
    initGreenMail(6666, ServerSetup.PROTOCOL_SMTP);

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
    mockConfig("demo", "demo", "from@localhost.com", "127.0.0.1", 5555, "SSL/TLS");
    initGreenMail(5555, ServerSetup.PROTOCOL_SMTPS);

    // when
    notificationService.notifyRecipient("some body text", "to@localhost.com");

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(GreenMailUtil.getBody(emails[0]), is("some body text"));
  }

  private void mockConfig(String username, String password, String address, String hostname, int port,
                          String protocol) {
    configurationService.setAlertEmailUsername(username);
    configurationService.setAlertEmailPassword(password);
    configurationService.setAlertEmailAddress(address);
    configurationService.setAlertEmailHostname(hostname);
    configurationService.setAlertEmailPort(port);
    configurationService.setAlertEmailProtocol(protocol);
  }


}
