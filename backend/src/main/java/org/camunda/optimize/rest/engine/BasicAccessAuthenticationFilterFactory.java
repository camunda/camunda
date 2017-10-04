package org.camunda.optimize.rest.engine;

import org.camunda.optimize.service.util.AbstractParametrizedFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
@Component
public class BasicAccessAuthenticationFilterFactory
    extends AbstractParametrizedFactory<BasicAccessAuthenticationFilter, String> {

  @Autowired
  private ConfigurationService configurationService;

  @Override
  protected BasicAccessAuthenticationFilter newInstance(String parameterValue) {
    return new BasicAccessAuthenticationFilter(parameterValue, configurationService);
  }

}
