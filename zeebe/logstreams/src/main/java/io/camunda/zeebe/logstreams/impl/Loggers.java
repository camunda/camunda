/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Loggers {

  public static final Logger LOGSTREAMS_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.logstreams");
  public static final Logger PROCESSOR_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.processor");
  public static final Logger SNAPSHOT_LOGGER =
      LoggerFactory.getLogger("io.camunda.zeebe.logstreams.snapshot");

  private Loggers() {}
}
