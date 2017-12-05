package org.camunda.optimize.service.util.configuration;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
public class ConfigurationServiceTest {

  @Test
  public void getSecret() throws Exception {
    ConfigurationService underTest = new ConfigurationService();
    assertThat(underTest.getLifetime(), is(15));
  }

  @Test
  public void testOverrideAliasOfEngine() {
    String[] locations = { "service-config.yaml", "environment-config.yaml", "override-engine-config.yaml" };
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getConfiguredEngines().size(), is(1));
  }

  @Test
  public void testOverride() {
    String[] locations = { "service-config.yaml", "environment-config.yaml", "override-test-config.yaml" };
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getLifetime(), is(10));
  }

  @Test
  public void testAllFieldsAreRead() throws Exception {
    String[] locations = { "service-config.yaml", "environment-config.yaml", "override-test-config.yaml" };
    ConfigurationService underTest = new ConfigurationService(locations);

    Method[] allMethods = ConfigurationService.class.getMethods();
    for(Method method : allMethods) {
      boolean isGetter = method.getName().startsWith("get") || method.getName().startsWith("is");
      if(isGetter && method.getParameterCount() == 0) {
        Object invoke = method.invoke(underTest);
        assertThat("invocation of [" + method.getName() + "]",invoke, is(notNullValue()));
      }
    }
  }

}