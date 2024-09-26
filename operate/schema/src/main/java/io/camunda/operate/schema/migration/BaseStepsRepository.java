/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import io.camunda.operate.exceptions.MigrationException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseStepsRepository implements StepsRepository {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  /**
   * Updates Steps in index by comparing steps in json format with documents from index. If there
   * are any new steps then they will be saved in index.
   */
  @Override
  public void updateSteps() throws IOException, MigrationException {
    final List<Step> stepsFromFiles = readStepsFromClasspath();
    final List<Step> stepsFromRepository = findAll();
    for (final Step step : stepsFromFiles) {
      if (!stepsFromRepository.contains(step)) {
        step.setCreatedDate(OffsetDateTime.now());
        logger.info("Add new step {} to repository.", step);
        save(step);
      }
    }
    refreshIndex();
  }
}
