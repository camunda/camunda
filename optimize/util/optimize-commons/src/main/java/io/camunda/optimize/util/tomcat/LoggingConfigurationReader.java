/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util.tomcat;

import com.google.common.collect.Lists;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingConfigurationReader {

  /* The order of this list is important */
  private final LinkedList<String> loggingConfigNames =
      Lists.newLinkedList(Arrays.asList("environment-log4j2.xml", "log4j2-test.xml", "log4j2.xml"));

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public LoggingConfigurationReader() {}

  public void defineLog4jLoggingConfiguration() {
    final InputStream configStream = getLog4j2ConfigurationFileStream();
    try {
      final ConfigurationSource configSource = new ConfigurationSource(configStream);
      final LoggerContext loggerContext = Configurator.initialize(null, configSource);
      enableElasticsearchRequestLogging(loggerContext);
    } catch (final IOException e) {
      // since logging setup broke, print it in standard error stream
      e.printStackTrace();
    }
  }

  private void enableElasticsearchRequestLogging(final LoggerContext loggerContext) {
    if (logger.isTraceEnabled()) {
      // this allows to enable logging of Elasticsearch requests when
      // Optimize log level is set to trace
      // for more information:
      // -
      // https://www.elastic.co/guide/en/elasticsearch/client/java-rest/6.5/java-rest-low-usage-logging.html
      // - https://guzalexander.com/2018/09/30/es-rest-client-trace-logging.html
      loggerContext.getLogger("tracer").setLevel(Level.TRACE);
    }
  }

  private InputStream getLog4j2ConfigurationFileStream() {
    InputStream inputStream = null;
    for (String loggingConfigName : loggingConfigNames) {
      inputStream = getClass().getClassLoader().getResourceAsStream(loggingConfigName);
      if (inputStream != null) {
        System.out.println("Found logging configuration file: " + loggingConfigName);
        System.setProperty("log4j.configurationFile", "classpath:" + loggingConfigName);
        return inputStream;
      }
    }

    throw new OptimizeRuntimeException("Logging configuration file not found");
  }
}
