package org.camunda.optimize.service.importing.fetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractEntityFetcher implements EngineEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(getClass());

  protected void logError(String message, Exception e) {
    if (logger.isDebugEnabled()) {
      logger.error(message, e);
    } else {
      logger.error(message);
    }
  }
}
