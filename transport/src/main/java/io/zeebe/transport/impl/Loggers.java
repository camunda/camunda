/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.util.ZbLogger;
import org.slf4j.Logger;

public final class Loggers {
  static final Logger TRANSPORT_LOGGER = new ZbLogger("io.zeebe.transport");

  private Loggers() {}
}
