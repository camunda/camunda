/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Loggers {
  public static final Logger CLUSTERING_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.clustering");
  public static final Logger SYSTEM_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.system");
  public static final Logger TRANSPORT_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.transport");
  public static final Logger PROCESS_REPOSITORY_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.process.repository");
  public static final Logger LOGSTREAMS_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.logstreams");

  public static final Logger EXPORTER_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.exporter");
  public static final Logger DELETION_SERVICE =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.logstreams.delete");
  public static final Logger RAFT = LoggerFactory.getLogger("io.camunda.zeebe.broker.raft");
  public static final Logger JOB_STREAM =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.jobStream");

  public static Logger getExporterLogger(final String exporterId) {
    final String loggerName = String.format("io.camunda.zeebe.broker.exporter.%s", exporterId);
    return LoggerFactory.getLogger(loggerName);
  }
}
