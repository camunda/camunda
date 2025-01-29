/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping(path = {"/v2/incident-alerting"})
public class IncidentAlertingController {

  private static final Map<String, AlertingChannel> STORAGE = new ConcurrentHashMap<>();

  @CamundaPostMapping(path = "/config")
  public ResponseEntity<Object> storeConfig(@RequestBody final IncidentAlertingConfig config) {
    STORAGE.put(config.getFilter().getProcessDefinitionKey(), config.getChannel());
    return ResponseEntity.ok().build();
  }

  @CamundaGetMapping()
  public ResponseEntity<Object> getConfig() {
    return ResponseEntity.ok(STORAGE);
  }
}

class IncidentAlertingConfig {
  private Filter filter;
  private AlertingChannel channel;

  public AlertingChannel getChannel() {
    return channel;
  }

  public void setChannel(final AlertingChannel channel) {
    this.channel = channel;
  }

  public Filter getFilter() {
    return filter;
  }

  public void setFilter(final Filter filter) {
    this.filter = filter;
  }
}

class Filter {
  private String processDefinitionKey;

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }
}

class AlertingChannel {
  private String email;

  public String getEmail() {
    return email;
  }

  public void setEmail(final String email) {
    this.email = email;
  }
}
