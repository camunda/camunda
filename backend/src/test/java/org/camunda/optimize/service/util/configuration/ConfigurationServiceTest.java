package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

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
    String[] locations = { "service-config.json", "environment-config.json", "override-engine-config.json" };
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getConfiguredEngines().size(), is(1));
  }

  @Test
  public void testOverride() {
    String[] locations = { "service-config.json", "environment-config.json", "override-test-config.json" };
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getLifetime(), is(10));
  }

  @Test
  public void testAllFieldsAreRead() throws Exception {
    String[] locations = { "service-config.json", "environment-config.json", "override-test-config.json" };
    ConfigurationService underTest = new ConfigurationService(locations);

    Method[] allMethods = ConfigurationService.class.getMethods();
    for(Method method : allMethods) {
      boolean isGetter = method.getName().startsWith("get") || method.getName().startsWith("is");
      if(isGetter && method.getParameterCount() == 0) {
        Object invoke = method.invoke(underTest);
        assertThat(invoke, is(notNullValue()));
      }
    }
  }

}