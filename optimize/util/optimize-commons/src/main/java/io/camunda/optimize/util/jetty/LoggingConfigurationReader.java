/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util.jetty;

import com.google.common.collect.Lists;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;

public class LoggingConfigurationReader {

  private final LinkedList<String> loggingConfigNames =
      Lists.newLinkedList(Arrays.asList("environment-log4j2.xml", "log4j2-test.xml", "log4j2.xml"));

  private final Logger logger = LogManager.getLogger(getClass());

  public LoggingConfigurationReader() {}

  public void defineLogbackLoggingConfiguration() {
    defineLog4j2LoggingConfiguration();
  }

  public void defineLog4j2LoggingConfiguration() {
    try (final InputStream configStream = getLog4j2ConfigurationFileStream()) {
      if (configStream != null) {
        final URI configUri = getClass().getClassLoader().getResource(getConfigFileName()).toURI();
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.setConfigLocation(configUri);
      }
    } catch (final IOException
        | NullPointerException
        | IllegalArgumentException
        | URISyntaxException e) {
      throw new OptimizeRuntimeException(e);
    }
    enableElasticsearchRequestLogging();
  }

  private void enableElasticsearchRequestLogging() {
    if (logger.isTraceEnabled()) {
      // Enable trace logging for Elasticsearch requests when set to trace
      Configurator.setLevel("tracer", Level.TRACE);
    }
  }

  private InputStream getLog4j2ConfigurationFileStream() {
    return loggingConfigNames.stream()
        .map(config -> getClass().getClassLoader().getResourceAsStream(config))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private String getConfigFileName() {
    return loggingConfigNames.stream()
        .filter(config -> getClass().getClassLoader().getResource(config) != null)
        .findFirst()
        .orElse("log4j2.xml");
  }
}
