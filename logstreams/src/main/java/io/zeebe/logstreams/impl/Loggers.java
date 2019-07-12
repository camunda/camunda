/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl;

import io.zeebe.util.ZbLogger;
import org.slf4j.Logger;

public class Loggers {
  public static final Logger LOGSTREAMS_LOGGER = new ZbLogger("io.zeebe.logstreams");
  public static final Logger PROCESSOR_LOGGER = new ZbLogger("io.zeebe.processor");
  public static final Logger ROCKSDB_LOGGER = new ZbLogger("io.zeebe.logstreams.rocksdb");
  public static final Logger SNAPSHOT_LOGGER = new ZbLogger("io.zeebe.logstreams.snapshot");
}
