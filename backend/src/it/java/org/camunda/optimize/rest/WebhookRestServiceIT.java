/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.Sets;
import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class WebhookRestServiceIT extends AbstractIT {
  private final String WEBHOOK_1_NAME = "webhook1";
  private final String WEBHOOK_2_NAME = "webhook2";

  @Test
  public void getAllWebhookNames() {
    // given
    Map<String, WebhookConfiguration> webhookMap = webhookClient.createSimpleWebhookConfigurationMap(Sets.newHashSet(
      WEBHOOK_2_NAME,
      WEBHOOK_1_NAME
    ));
    embeddedOptimizeExtension.getConfigurationService().setConfiguredWebhooks(webhookMap);

    // when
    List<String> allWebhooks = webhookClient.getAllWebhooks();

    // then
    assertThat(allWebhooks).containsExactly(WEBHOOK_1_NAME, WEBHOOK_2_NAME);
  }

  @Test
  public void getAllWebhookNamesWithoutAuthorisation() {
    // given
    Map<String, WebhookConfiguration> webhookMap = webhookClient.createSimpleWebhookConfigurationMap(Sets.newHashSet(
      WEBHOOK_2_NAME,
      WEBHOOK_1_NAME
    ));
    embeddedOptimizeExtension.getConfigurationService().setConfiguredWebhooks(webhookMap);

    // when
    Response webhookResponse = webhookClient.getAllWebhooksWithoutAuthentication();

    // then
    assertThat(webhookResponse.getStatus()).isEqualTo(HttpStatus.SC_UNAUTHORIZED);
  }
}
