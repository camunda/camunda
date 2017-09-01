package org.camunda.optimize.rest.engine;

import org.camunda.optimize.service.util.Factory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@Component
public class BasicAccessAuthenticationFilterFactory implements Factory<BasicAccessAuthenticationFilter, String> {

  protected Map<String, BasicAccessAuthenticationFilter> instances = new HashMap<>();

  @Autowired
  private ConfigurationService configurationService;

  @Override
  public BasicAccessAuthenticationFilter getInstance(String engineAlias) {
    if (!instances.containsKey(engineAlias)) {
      instances.put(engineAlias, new BasicAccessAuthenticationFilter(engineAlias, configurationService));
    }
    return instances.get(engineAlias);
  }
}
