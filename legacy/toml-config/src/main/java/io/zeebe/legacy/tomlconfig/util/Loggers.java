/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig.util;

import io.zeebe.util.ZbLogger;
import org.slf4j.Logger;

@Deprecated(since = "0.23.0-alpha2", forRemoval = true)
/* Kept in order to be able to offer a migration path for old configurations.
 */
public final class Loggers {

  public static final Logger CONFIG_LOGGER = new ZbLogger("io.zeebe.legacy.tomlconfig.util");
  public static final Logger LEGACY_LOGGER = new ZbLogger("io.zeebe.legacy");
}
