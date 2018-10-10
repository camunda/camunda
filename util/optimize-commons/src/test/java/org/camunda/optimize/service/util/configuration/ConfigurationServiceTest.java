package org.camunda.optimize.service.util.configuration;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;


public class ConfigurationServiceTest {

  @Test
  public void getSecret() {
    ConfigurationService underTest = new ConfigurationService();
    assertThat(underTest.getTokenLifeTime(), is(15));
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
    assertThat(underTest.getTokenLifeTime(), is(10));
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

  @Test
  public void testCutTrailingSlash() {
    // given
    String[] locations = {"override-engine-config.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);

    // when
    String resultUrl =
            underTest.getConfiguredEngines().get("myAwesomeEngine").getWebapps().getEndpoint();

    // then
    assertThat(resultUrl.endsWith("/"), is(false));
  }
}