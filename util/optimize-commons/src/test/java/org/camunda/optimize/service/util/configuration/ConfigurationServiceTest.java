package org.camunda.optimize.service.util.configuration;

import com.jayway.jsonpath.spi.mapper.MappingException;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;


public class ConfigurationServiceTest {

  @Test
  public void getSecret() {
    ConfigurationService underTest = new ConfigurationService();
    assertThat(underTest.getTokenLifeTimeMinutes(), is(15));
  }

  @Test
  public void testOverrideAliasOfEngine() {
    String[] locations = {"service-config.yaml", "environment-config.yaml", "override-engine-config.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getConfiguredEngines().size(), is(1));
  }

  @Test
  public void certificateAuthorizationCanBeAList() {
    String[] locations = {"config-samples/certificate-authorities/ca-auth-as-list.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities().size(), is(2));
  }

  @Test
  public void certificateAuthorizationStringIsConvertedToList() {
    String[] locations = {"config-samples/certificate-authorities/ca-auth-as-string-is-converted.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities().size(), is(1));
  }

  @Test(expected = MappingException.class)
  public void wrongCaAuthFormatThrowsError() {
    String[] locations = {"config-samples/certificate-authorities/wrong-ca-auth-format-throws-error.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    underTest.getElasticsearchSecuritySSLCertificateAuthorities();
  }

  @Test(expected = MappingException.class)
  public void wrongCaAuthListFormatThrowsError() {
    String[] locations = {"config-samples/certificate-authorities/wrong-ca-auth-list-format-throws-error.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    underTest.getElasticsearchSecuritySSLCertificateAuthorities();
  }

  @Test
  public void testOverride() {
    String[] locations = {"service-config.yaml", "environment-config.yaml", "override-test-config.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getTokenLifeTimeMinutes(), is(10));
  }

  @Test
  public void testAllFieldsAreRead() throws Exception {
    String[] locations = {"service-config.yaml", "environment-config.yaml", "override-test-config.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);

    Method[] allMethods = ConfigurationService.class.getMethods();
    for (Method method : allMethods) {
      boolean isGetter = method.getName().startsWith("get") || method.getName().startsWith("is");
      if (isGetter && method.getParameterCount() == 0) {
        Object invoke = method.invoke(underTest);
        assertThat("invocation of [" + method.getName() + "]", invoke, is(notNullValue()));
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

  @Test
  public void testDeprecatedLeafKeyForConfigurationLeafKey() {
    // given
    String[] locations = {"config-samples/config-alerting-leaf-key.yaml"};
    String[] deprecatedLocations = {"deprecation-samples/deprecated-alerting-leaf-key.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations, deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(underTest);

    // then
    assertThat(deprecations.size(), is(1));
    assertThat(
      deprecations.get("alerting.email.username"),
      is(generateExpectedDocUrl("/technical-guide/configuration/#email"))
    );
  }

  @Test
  public void testDeprecatedParentKeyForConfigurationLeafKey() {
    // given
    String[] locations = {"config-samples/config-alerting-leaf-key.yaml"};
    String[] deprecatedLocations = {"deprecation-samples/deprecated-alerting-parent-key.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations, deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(underTest);

    // then
    assertThat(deprecations.size(), is(1));
    assertThat(deprecations.get("alerting.email"), is(generateExpectedDocUrl("/technical-guide/configuration/#email")));
  }

  @Test
  public void testDeprecatedParentKeyForConfigurationParentKey_onlyOneDeprecationResult() {
    // given
    String[] locations = {"config-samples/config-alerting-parent-with-leafs-key.yaml"};
    String[] deprecatedLocations = {"deprecation-samples/deprecated-alerting-parent-key.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations, deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(underTest);

    // then
    assertThat(deprecations.size(), is(1));
    assertThat(deprecations.get("alerting.email"), is(generateExpectedDocUrl("/technical-guide/configuration/#email")));
  }

  @Test
  public void testAllDeprecationsForDistinctPathsArePresent() {
    // given
    String[] locations = {
      "config-samples/config-alerting-parent-with-leafs-key.yaml",
      "config-samples/config-somethingelse-parent-with-leafs-key.yaml"
    };
    String[] deprecatedLocations = {
      "deprecation-samples/deprecated-alerting-parent-key.yaml",
      "deprecation-samples/deprecated-somethingelse-parent-key.yaml"
    };
    ConfigurationService underTest = new ConfigurationService(locations, deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(underTest);

    // then
    assertThat(deprecations.size(), is(2));
    assertThat(deprecations.get("alerting.email"), is(generateExpectedDocUrl("/technical-guide/configuration/#email")));
    assertThat(
      deprecations.get("somethingelse.email"),
      is(generateExpectedDocUrl("/technical-guide/configuration/#somethingelse"))
    );

  }

  @Test
  public void testDeprecatedArrayLeafKey() {
    // given
    String[] locations = {"config-samples/config-tcpPort-leaf-key.yaml"};
    String[] deprecatedLocations = {"deprecation-samples/deprecated-tcpPort-wildcard-leaf-key.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations, deprecatedLocations);

    // when
    Map<String, String> deprecations = validateForAndReturnDeprecationsFailIfNone(underTest);

    // then
    assertThat(deprecations.size(), is(1));
    assertThat(
      deprecations.get("es.connection.nodes[*].tcpPort"),
      is(generateExpectedDocUrl("/technical-guide/setup/configuration/#connection-settings"))
    );
  }

  @Test
  public void testNonDeprecatedArrayLeafKey_allFine() {
    // given
    String[] locations = {"config-samples/config-wo-tcpPort-leaf-key.yaml"};
    String[] deprecatedLocations = {"deprecation-samples/deprecated-tcpPort-wildcard-leaf-key.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations, deprecatedLocations);

    // when
    Optional<Map<String, String>> deprecations = validateForAndReturnDeprecations(underTest);

    // then
    assertThat(deprecations.isPresent(), is(false));
  }

  @Test
  public void testAllFineOnEmptyDeprecationConfig() {
    // given
    String[] locations = {"config-samples/config-alerting-leaf-key.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations, new String[]{});

    // when
    Optional<Map<String, String>> deprecations = validateForAndReturnDeprecations(underTest);

    // then
    assertThat(deprecations.isPresent(), is(false));
  }

  private Map<String, String> validateForAndReturnDeprecationsFailIfNone(ConfigurationService underTest) {
    return validateForAndReturnDeprecations(underTest)
      .orElseThrow(() -> new RuntimeException("Validation succeeded although it should have failed"));
  }

  private Optional<Map<String, String>> validateForAndReturnDeprecations(ConfigurationService underTest) {
    try {
      underTest.validateNoDeprecatedConfigKeysUsed();
      return Optional.empty();
    } catch (OptimizeConfigurationException e) {
      return Optional.of(e.getDeprecatedKeysAndDocumentationLink());
    }
  }

  private String generateExpectedDocUrl(String path) {
    return ConfigurationService.DOC_URL + path;
  }

}