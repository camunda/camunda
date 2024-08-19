/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.digest;

import org.slf4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DigestTask implements Runnable {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DigestTask.class);
  private final DigestService digestService;
  private final String processDefinitionKey;

  public DigestTask(final DigestService digestService, final String processDefinitionKey) {
    this.digestService = digestService;
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public void run() {
    digestService.handleDigestTask(processDefinitionKey);
  }
}
