package org.camunda.optimize.test.it.factory;

import org.camunda.optimize.service.security.TokenService;
import org.camunda.optimize.service.util.ConfigurationService;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Askar Akhmerov
 */
public class SpyTokenServiceFactory implements FactoryBean<TokenService> {
  private TokenService tokenService;

  @Autowired
  private ConfigurationService configurationService;

  @Override
  public TokenService getObject() throws Exception {
    if (tokenService == null) {
      tokenService = new TokenService();
      tokenService.setConfigurationService(configurationService);
      tokenService = Mockito.spy(tokenService);
    }
    return tokenService;
  }

  @Override
  public Class<?> getObjectType() {
    return TokenService.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
