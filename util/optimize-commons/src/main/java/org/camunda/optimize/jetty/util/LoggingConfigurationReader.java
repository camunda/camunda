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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;


public class LoggingConfigurationReader {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public void defineLogbackLoggingConfiguration() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.reset();
    JoranConfigurator configurator = new JoranConfigurator();
    InputStream configStream = null;
    try {
      configStream = getLogbackConfigurationFileStream();
      configurator.setContext(loggerContext);
      configurator.doConfigure(configStream); // loads logback file
      Objects.requireNonNull(configStream).close();
    } catch (JoranException | IOException e) {
      //since logging setup broke, print it in standard error stream
      e.printStackTrace();
    } finally {
      if (configStream != null) {
        try {
          configStream.close();
        } catch (IOException e) {
          logger.error("error closing stream", e);
        }
      }
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
    InputStream stream = this.getClass()
      .getClassLoader()
      .getResourceAsStream("environment-logback.xml");
    if (stream != null) {
      return stream;
    }
    stream = this.getClass().getClassLoader().getResourceAsStream("logback-test.xml");
    if (stream != null) {
      return stream;
    }
    stream = this.getClass().getClassLoader().getResourceAsStream("logback.xml");
    if (stream != null) {
      return stream;
    }
    return null;
  }
}
