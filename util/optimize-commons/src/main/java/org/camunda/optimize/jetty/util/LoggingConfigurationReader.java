package org.camunda.optimize.jetty.util;

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
