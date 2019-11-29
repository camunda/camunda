/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;


public class LoggingConfigurationReader {

  private LinkedList<String> loggingConfigNames = Lists.newLinkedList(Arrays.asList(
    "environment-logback.xml",
    "logback-test.xml",
    "logback.xml"
  ));

  private Logger logger = LoggerFactory.getLogger(getClass());

  public LoggingConfigurationReader() {
  }

  public LoggingConfigurationReader(String configXmlName) {
    this.loggingConfigNames.addFirst(configXmlName);
  }


  public void defineLogbackLoggingConfiguration() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.reset();
    JoranConfigurator configurator = new JoranConfigurator();
    try (InputStream configStream = getLogbackConfigurationFileStream()) {
      configurator.setContext(loggerContext);
      configurator.doConfigure(configStream); // loads logback file
      Objects.requireNonNull(configStream).close();
    } catch (JoranException | IOException e) {
      //since logging setup broke, print it in standard error stream
      e.printStackTrace();
    }
    enableElasticsearchRequestLogging(loggerContext);
  }

  private void enableElasticsearchRequestLogging(LoggerContext loggerContext) {
    if (logger.isTraceEnabled()) {
      // this allows to enable logging of Elasticsearch requests when
      // Optimize log level is set to trace
      // for more information:
      // - https://www.elastic.co/guide/en/elasticsearch/client/java-rest/6.5/java-rest-low-usage-logging.html
      // - https://guzalexander.com/2018/09/30/es-rest-client-trace-logging.html
      loggerContext.getLogger("tracer").setLevel(Level.TRACE);
    }
  }

  private InputStream getLogbackConfigurationFileStream() {
    return loggingConfigNames.stream()
      .map(config -> this.getClass().getClassLoader().getResourceAsStream(config))
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }
}
