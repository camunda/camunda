package org.camunda.optimize.service.engine.importing.fetcher;

import org.camunda.optimize.rest.engine.EngineClientFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.client.Client;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractEngineAwareFetcher {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected String engineAlias;

  @Autowired
  protected EngineClientFactory engineClientFactory;
  @Autowired
  protected ConfigurationService configurationService;

  public AbstractEngineAwareFetcher(String engineAlias) {
    this.engineAlias = engineAlias;
  }

  public Client getEngineClient() {
    return engineClientFactory.getInstance(this.getEngineAlias());
  }

  public String getEngineAlias() {
    return engineAlias;
  }
}
