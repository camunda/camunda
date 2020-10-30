/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Loggers {
  public static final Logger CLUSTERING_LOGGER =
      LoggerFactory.getLogger("io.zeebe.broker.clustering");
  public static final Logger SYSTEM_LOGGER = LoggerFactory.getLogger("io.zeebe.broker.system");
  public static final Logger TRANSPORT_LOGGER =
      LoggerFactory.getLogger("io.zeebe.broker.transport");
  public static final Logger WORKFLOW_REPOSITORY_LOGGER =
      LoggerFactory.getLogger("io.zeebe.broker.workflow.repository");

  public static final Logger EXPORTER_LOGGER = LoggerFactory.getLogger("io.zeebe.broker.exporter");
  public static final Logger DELETION_SERVICE =
      LoggerFactory.getLogger("io.zeebe.broker.logstreams.delete");

  public static Logger getExporterLogger(final String exporterId) {
    final String loggerName = String.format("io.zeebe.broker.exporter.%s", exporterId);
    return LoggerFactory.getLogger(loggerName);
  }
}
