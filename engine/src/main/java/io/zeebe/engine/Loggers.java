/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine;

import io.zeebe.util.ZbLogger;
import org.slf4j.Logger;

public class Loggers {
  public static final Logger STREAM_PROCESSING = new ZbLogger("io.zeebe.broker.logstreams");
  public static final Logger WORKFLOW_REPOSITORY_LOGGER =
      new ZbLogger("io.zeebe.broker.workflow.repository");

  public static final Logger WORKFLOW_PROCESSOR_LOGGER = new ZbLogger("io.zeebe.broker.workflow");

  public static final Logger getExporterLogger(String exporterId) {
    final String loggerName = String.format("io.zeebe.broker.exporter.%s", exporterId);
    return new ZbLogger(loggerName);
  }
}
