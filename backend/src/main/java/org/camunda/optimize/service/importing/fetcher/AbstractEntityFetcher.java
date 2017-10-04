package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.ws.rs.client.Client;

/**
 * @author Askar Akhmerov
 */
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class AbstractEntityFetcher implements EngineEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private EngineClientFactory engineClientFactory;

  protected void logError(String message, Exception e) {
    if (logger.isDebugEnabled()) {
      logger.error(message, e);
    } else {
      logger.error(message);
    }
  }

  protected Client getEngineClient(String engineAlias) {
    return engineClientFactory.getInstance(engineAlias);
  }

}
