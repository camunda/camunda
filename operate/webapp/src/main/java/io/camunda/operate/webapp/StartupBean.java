/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.search.schema.SchemaManagerContainer;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @deprecated This class is deprecated and will be removed in version 8.10.
 *     <p>It is not safe to remove this class yet because the Operation Executor is still required
 *     to process any pending operation batches in the `operate-operation` index. Removing it now
 *     could result in user-initiated operations remaining unprocessed after an upgrade, breaking
 *     user-space and leaving operations stuck indefinitely. The class should only be removed once
 *     it is guaranteed that no pending batches remain and all consumers have migrated to the V2
 *     API. See https://github.com/camunda/camunda/issues/44958 for migration progress and details.
 */
@Component
@DependsOn("searchEngineSchemaInitializer")
@Profile({"!test", "test-executor"})
@ConditionalOnRdbmsDisabled
@Deprecated(forRemoval = true, since = "8.9")
public class StartupBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartupBean.class);

  @Autowired private OperationExecutor operationExecutor;

  @Autowired private SchemaManagerContainer schemaManagerContainer;

  @PostConstruct
  public void initApplication() {
    if (!schemaManagerContainer.isInitialized()) {
      LOGGER.info(
          "INIT: Skipping operation executor start - search engine schema not initialized (application may be shutting down).");
      return;
    }
    LOGGER.info("INIT: Start operation executor...");
    operationExecutor.startExecuting();
    LOGGER.info("INIT: DONE");
  }
}
