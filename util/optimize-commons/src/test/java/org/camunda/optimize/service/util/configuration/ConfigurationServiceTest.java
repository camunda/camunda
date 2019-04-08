/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import com.jayway.jsonpath.spi.mapper.MappingException;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


public class ConfigurationServiceTest {

  private static final int CUSTOM_AUTH_TOKEN_LIFEMIN = 6;
  private static final Boolean CUSTOM_FIRST_ENGINE_IMPORT_ENABLED = true;
  private static final Boolean CUSTOM_SECOND_ENGINE_IMPORT_ENABLED = false;
  private static final String CUSTOM_FIRST_ES_HOST = "localhost";
  private static final int CUSTOM_FIRST_ES_PORT = 9201;
  private static final String CUSTOM_SECOND_ES_HOST = "otherHost";
  private static final int CUSTOM_SECOND_ES_PORT = 9202;
  private static final String CUSTOM_AUTH_AUTHORITY_2 = "auth2";
  private static final String CUSTOM_AUTH_AUTHORITY_3 = "auth3";

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Test
  public void getTokenLifeTimeMinutes() {
    ConfigurationService underTest = new ConfigurationService();
    assertThat(underTest.getTokenLifeTimeMinutes(), is(15));
  }

  @Test
  public void testOverrideAliasOfEngine() {
    String[] locations = {"service-config.yaml", "environment-config.yaml", "override-engine-config.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    assertThat(underTest.getConfiguredEngines().size(), is(1));
    assertThat(underTest.getConfiguredEngines().get("myAwesomeEngine").getName(), is(notNullValue()));
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
  public void disableHttpPort() {
    String[] possibilitiesToDisableHttpPortConnection = {
      "config-samples/port/empty-http-port.yaml",
      "config-samples/port/null-http-port.yaml"
    };
    for (String configLocation : possibilitiesToDisableHttpPortConnection) {
      // given
      ConfigurationService underTest = new ConfigurationService(new String[]{configLocation});

      // when
      Optional<Integer> containerHttpPort = underTest.getContainerHttpPort();

      // then
      assertTrue(!containerHttpPort.isPresent());
    }
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void invalidHttpsPortThrowsError() {
    String[] locations = {"config-samples/port/invalid-https-port.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    underTest.getContainerHttpsPort();
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void invalidElasticsearchProxyConfigThrowsError() {
    String[] locations = {"config-samples/config-invalid-elasticsearch-proxy-config.yaml"};
    ConfigurationService underTest = new ConfigurationService(locations);
    underTest.getElasticSearchProxyConfig();
  }

  @Test
  public void resolvePropertiesFromEnvironmentVariables() {
    //when
    final String[] locations = {"service-config.yaml", "environment-variable-test-config.yaml"};
    environmentVariables.set("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    environmentVariables.set("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    environmentVariables.set("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    environmentVariables.set("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    environmentVariables.set("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    environmentVariables.set("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    environmentVariables.set("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    environmentVariables.set("CERT_AUTHORITY_2", CUSTOM_AUTH_AUTHORITY_2);
    environmentVariables.set("CERT_AUTHORITY_3", CUSTOM_AUTH_AUTHORITY_3);
    final ConfigurationService underTest = new ConfigurationService(locations);

    // then
    assertThatVariablePlaceHoldersAreResolvedCorrectly(underTest);
  }

  @Test
  public void resolvePropertiesFromSystemVariables() {
    //when
    final String[] locations = {"service-config.yaml", "environment-variable-test-config.yaml"};
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("CERT_AUTHORITY_2", CUSTOM_AUTH_AUTHORITY_2);
    System.setProperty("CERT_AUTHORITY_3", CUSTOM_AUTH_AUTHORITY_3);
    final ConfigurationService underTest = new ConfigurationService(locations);

    // then
    assertThatVariablePlaceHoldersAreResolvedCorrectly(underTest);
  }

  @Test
  public void resolvePropertiesFromSystemVariablesWinOverEnvironmentVariables() {
    //when
    final String[] locations = {"service-config.yaml", "environment-variable-test-config.yaml"};
    environmentVariables.set("AUTH_TOKEN_LIFEMIN", "wrong");
    environmentVariables.set("IMPORT_ENABLED_1", "wrong");
    environmentVariables.set("IMPORT_ENABLED_2", "wrong");
    environmentVariables.set("ES_HOST_1", "wrong");
    environmentVariables.set("ES_PORT_1", "wrong");
    environmentVariables.set("ES_HOST_2", "wrong");
    environmentVariables.set("ES_PORT_2", "wrong");
    environmentVariables.set("CERT_AUTHORITY_2", "wrong");
    environmentVariables.set("CERT_AUTHORITY_3", "wrong");
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFEMIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("CERT_AUTHORITY_2", CUSTOM_AUTH_AUTHORITY_2);
    System.setProperty("CERT_AUTHORITY_3", CUSTOM_AUTH_AUTHORITY_3);
    final ConfigurationService underTest = new ConfigurationService(locations);

    // then
    assertThatVariablePlaceHoldersAreResolvedCorrectly(underTest);
  }

  @Test
  public void failOnMissingSystemOrEnvironmentVariable() {
    //when
    final String[] locations = {"service-config.yaml", "environment-variable-test-config.yaml"};
    OptimizeConfigurationException configurationException = null;
    try {
      final ConfigurationService underTest = new ConfigurationService(locations);
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    }
    // then
    assertThat(configurationException, is(notNullValue()));
    assertThat(
      configurationException.getMessage(),
      containsString("Could not resolve system/environment variable")
    );
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

  private void assertThatVariablePlaceHoldersAreResolvedCorrectly(final ConfigurationService underTest) {
    assertThat(underTest.getTokenLifeTimeMinutes(), is(CUSTOM_AUTH_TOKEN_LIFEMIN));
    assertThat(
      underTest.getConfiguredEngines().values().stream().map(EngineConfiguration::isImportEnabled).collect(toList()),
      contains(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED, CUSTOM_SECOND_ENGINE_IMPORT_ENABLED)
    );
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHost)
        .collect(toList()),
      contains(CUSTOM_FIRST_ES_HOST, CUSTOM_SECOND_ES_HOST)
    );
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHttpPort)
        .collect(toList()),
      contains(CUSTOM_FIRST_ES_PORT, CUSTOM_SECOND_ES_PORT)
    );
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