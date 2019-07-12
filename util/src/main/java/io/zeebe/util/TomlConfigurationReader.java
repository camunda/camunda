/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import com.moandjiezana.toml.Toml;
import java.io.File;
import java.io.InputStream;
import org.slf4j.Logger;

public class TomlConfigurationReader {
  public static final Logger LOG = Loggers.CONFIG_LOGGER;

  public static <T> T read(String filePath, Class<T> type) {
    final File file = new File(filePath);

    LOG.debug("Reading configuration for {} from file {} ", type, file.getAbsolutePath());

    return new Toml().read(file).to(type);
  }

  public static <T> T read(InputStream configStream, Class<T> type) {
    LOG.debug("Reading configuration for {} from input stream", type);

    return new Toml().read(configStream).to(type);
  }
}
