package org.camunda.optimize.service.engine.importing.fetcher;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.count.cache.InstanceCountCache;
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

  protected EngineContext engineContext;

  @Autowired
  protected InstanceCountCache cache;
  @Autowired
  protected ConfigurationService configurationService;

  public AbstractEngineAwareFetcher(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  public Client getEngineClient() {
    return engineContext.getEngineClient();
  }

  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  public void reset() {
    cache.reset();
  }
}
