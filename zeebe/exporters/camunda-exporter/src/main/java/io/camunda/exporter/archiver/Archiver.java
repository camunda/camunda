/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Archiver {
  protected static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger LOGGER = LoggerFactory.getLogger(Archiver.class);
  private final ArchiverRepository archiverRepository;

  private final ScheduledExecutorService archiverExecutor;
  private final int partitionId;

  private final List<ArchiverJob> archiverJobs;

  public Archiver(
      final ArchiverRepository archiverRepository,
      final ScheduledExecutorService archiverExecutor,
      final int partitionId,
      final List<ArchiverJob> archiverJobs) {
    this.archiverRepository = archiverRepository;
    this.archiverExecutor = archiverExecutor;
    this.partitionId = partitionId;
    this.archiverJobs = archiverJobs;
  }

  public void startArchiving() {
    //  TODO: if (operateProperties.getArchiver().isRolloverEnabled()) {
    LOGGER.info("INIT: Start archiving data...");

    LOGGER.info("Starting archiver for partition: {}", partitionId);

    for (final var archiverJob : archiverJobs) {
      archiverExecutor.execute(archiverJob);
    }

    // }
  }

  public void stopArchiving() {
    // TODO: check if we need to do more during closing
    archiverJobs.forEach(a -> a.shutdown());
    archiverExecutor.shutdownNow();
  }
}
