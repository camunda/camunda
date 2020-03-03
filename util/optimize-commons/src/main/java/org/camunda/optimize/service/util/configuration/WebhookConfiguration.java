/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.Data;

import java.util.Map;

@Data
public class WebhookConfiguration {
  public final static String ALERT_MESSAGE_PLACEHOLDER = "{{ALERT_MESSAGE}}";

  private String url;
  private Map<String, String> headers;
  private String httpMethod;
  private String defaultPayload;
}
