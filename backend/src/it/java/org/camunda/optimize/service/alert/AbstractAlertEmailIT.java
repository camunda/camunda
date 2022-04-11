/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public class AbstractAlertEmailIT extends AbstractAlertIT {
  protected static GreenMail greenMail;

  @BeforeAll
  public static void beforeAll() {
    initGreenMail();
  }

  @AfterAll
  public static void afterAll() {
    greenMail.stop();
  }

  @BeforeEach
  public void beforeEachGreenMailSetup() {
    greenMail.reset();
    setupGreenMailUsers();
  }

  private static void initGreenMail() {
    greenMail = new GreenMail(
      new ServerSetup(IntegrationTestConfigurationUtil.getSmtpPort(), null, ServerSetup.PROTOCOL_SMTP)
    );
  }

  private static void setupGreenMailUsers() {
    greenMail.setUser("from@localhost.com", "demo", "demo");
    greenMail.setUser("test@camunda.com", "test@camunda.com", "test@camunda.com");
  }

}
