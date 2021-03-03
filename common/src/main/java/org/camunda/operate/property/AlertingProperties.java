/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

public class AlertingProperties {

  private String webhook;

  public String getWebhook() {
    return webhook;
  }

  public AlertingProperties setWebhook(final String webhook) {
    this.webhook = webhook;
    return this;
  }
}
