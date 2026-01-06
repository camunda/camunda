/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@DependsOn("searchEngineSchemaInitializer")
@Profile({"!test", "test-executor"})
@ConditionalOnRdbmsDisabled
public class StartupBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartupBean.class);

  @Autowired private OperationExecutor operationExecutor;

  @PostConstruct
  public void initApplication() {
    LOGGER.info("INIT: Start operation executor...");
    operationExecutor.startExecuting();
    LOGGER.info("INIT: DONE");
  }
}
