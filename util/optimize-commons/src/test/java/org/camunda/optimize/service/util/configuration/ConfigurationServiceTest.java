/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.spi.mapper.MappingException;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.service.util.configuration.extension.EnvironmentVariablesExtension;
import org.camunda.optimize.service.util.configuration.extension.SystemPropertiesExtension;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.reflect.Method;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;

public class ConfigurationServiceTest {

  private static final int DEFAULT_AUTH_TOKEN_LIFE_MIN = 5;
  private static final int CUSTOM_AUTH_TOKEN_LIFE_MIN = 6;
  private static final String TOKEN_SECRET = "someSecret";
  private static final long HSTS_MAX_AGE = 31536000;
  private static final Boolean DEFAULT_FIRST_ENGINE_IMPORT_ENABLED = false;
  private static final Boolean CUSTOM_FIRST_ENGINE_IMPORT_ENABLED = true;
  private static final Boolean DEFAULT_SECOND_ENGINE_IMPORT_ENABLED = true;
  private static final Boolean CUSTOM_SECOND_ENGINE_IMPORT_ENABLED = false;
  private static final String DEFAULT_FIRST_ES_HOST = "default1";
  private static final String CUSTOM_FIRST_ES_HOST = "localhost";
  private static final int DEFAULT_FIRST_ES_PORT = 9200;
  private static final int CUSTOM_FIRST_ES_PORT = 9201;
  private static final String DEFAULT_SECOND_ES_HOST = "default2";
  private static final String CUSTOM_SECOND_ES_HOST = "otherHost";
  private static final int DEFAULT_SECOND_ES_PORT = 9200;
  private static final int CUSTOM_SECOND_ES_PORT = 9202;
  // note: these are not valid package names but just serve the purpose of special character handling on parsing
  private static final String DEFAULT_PACKAGE_2 = "package:2";
  private static final String CUSTOM_PACKAGE_2 = "pack2";
  private static final String DEFAULT_PACKAGE_3 = "";
  private static final String CUSTOM_PACKAGE_3 = "pack_3";
  private static final String API_SECRET = "secret";
  private static final String ACCESS_URL = "accessUrl";
  private static final String CUSTOM_EVENT_BASED_USER_IDS = "[demo,kermit]";
  private static final String CUSTOM_SUPER_USER_IDS = "[demo, kermit]";
  private static final String CUSTOM_SUPER_GROUP_IDS = "[demoGroup, kermitGroup]";
  private static final Boolean CUSTOM_ZEEBE_ENABLED = true;
  private static final String CUSTOM_ZEEBE_RECORD_PREFIX = "custom-record-prefix";
  private static final int CUSTOM_ZEEBE_PARTITION_COUNT = 2;
  private static final int CUSTOM_ZEEBE_IMPORT_PAGE_SIZE = 5;
  private static final String CUSTOM_ES_USERNAME = "username";
  private static final String CUSTOM_ES_PASSWORD = "password";
  private static final Boolean CUSTOM_ES_SSL_ENABLED = true;
  private static final Boolean CUSTOM_SHARING_ENABLED = true;
  private static final Boolean CUSTOM_UI_LOGOUT_HIDDEN = true;
  private static final String CUSTOM_REPOSITORY_NAME = "snapshotRepoName";
  public static final int CUSTOM_CONTAINER_HTTP_PORT = 9876;
  public static final int CUSTOM_CONTAINER_HTTPS_PORT = 9877;
  public static final String CUSTOM_CONTAINER_KEYSTORE_PASSWORD = "customPassword";
  public static final int CUSTOM_CONTAINER_MAX_STATUS_CONNECTIONS = 5;

  @RegisterExtension
  @Order(1)
  public EnvironmentVariablesExtension environmentVariablesExtension = new EnvironmentVariablesExtension();

  @RegisterExtension
  @Order(2)
  public SystemPropertiesExtension systemPropertiesExtension = new SystemPropertiesExtension();

  @Test
  public void getTokenLifeTimeMinutes() {
    ConfigurationService underTest = createDefaultConfiguration();
    assertThat(underTest.getAuthConfiguration().getTokenLifeTimeMinutes()).isEqualTo(60);
  }

  @Test
  public void testOverrideAliasOfEngine() {
    String[] locations = {defaultConfigFile(), "environment-config.yaml", "override-engine-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getConfiguredEngines()).hasSize(1);
    assertThat(underTest.getConfiguredEngines().get("myAwesomeEngine").getName()).isNotNull();
  }

  @Test
  public void certificateAuthorizationCanBeAList() {
    String[] locations = {defaultConfigFile(), "config-samples/certificate-authorities/ca-auth-as-list.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities()).hasSize(2);
  }

  @Test
  public void certificateAuthorizationStringIsConvertedToList() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/ca-auth-as-string-is-converted.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities()).hasSize(1);
  }

  @Test
  public void wrongCaAuthFormatThrowsError() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/wrong-ca-auth-format-throws-error.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThatThrownBy(underTest::getElasticsearchSecuritySSLCertificateAuthorities).isInstanceOf(MappingException.class);
  }

  @Test
  public void wrongCaAuthListFormatThrowsError() {
    String[] locations = {defaultConfigFile(),
      "config-samples/certificate-authorities/wrong-ca-auth-list-format-throws-error.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThatThrownBy(underTest::getElasticsearchSecuritySSLCertificateAuthorities).isInstanceOf(MappingException.class);
  }

  @Test
  public void disableHttpPort() {
    String[] possibilitiesToDisableHttpPortConnection = {
      "config-samples/port/empty-http-port.yaml",
      "config-samples/port/null-http-port.yaml"
    };
    for (String configLocation : possibilitiesToDisableHttpPortConnection) {
      // given
      ConfigurationService underTest = createConfiguration(new String[]{defaultConfigFile(), configLocation});

      // when
      Optional<Integer> containerHttpPort = underTest.getContainerHttpPort();

      // then
      assertThat(containerHttpPort).isNotPresent();
    }
  }

  @Test
  public void invalidHttpsPortThrowsError() {
    String[] locations = {defaultConfigFile(), "config-samples/port/invalid-https-port.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThatThrownBy(underTest::getContainerHttpsPort).isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void invalidElasticsearchProxyConfigThrowsError() {
    String[] locations = {defaultConfigFile(), "config-samples/config-invalid-elasticsearch-proxy-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThatThrownBy(underTest::getElasticsearchProxyConfig).isInstanceOf(OptimizeConfigurationException.class);
  }

  @Test
  public void resolvePropertiesFromEnvironmentVariables() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    environmentVariablesExtension.set("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFE_MIN));
    environmentVariablesExtension.set("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    environmentVariablesExtension.set("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    environmentVariablesExtension.set("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    environmentVariablesExtension.set("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    environmentVariablesExtension.set("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    environmentVariablesExtension.set("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    environmentVariablesExtension.set("PACKAGE_2", CUSTOM_PACKAGE_2);
    environmentVariablesExtension.set("PACKAGE_3", CUSTOM_PACKAGE_3);
    environmentVariablesExtension.set("OPTIMIZE_API_ACCESS_TOKEN", API_SECRET);
    environmentVariablesExtension.set("ACCESS_URL", ACCESS_URL);
    environmentVariablesExtension.set("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS", CUSTOM_EVENT_BASED_USER_IDS);
    environmentVariablesExtension.set("OPTIMIZE_SUPER_USER_IDS", CUSTOM_SUPER_USER_IDS);
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ENTERPRISE", String.valueOf(false));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED", String.valueOf(true));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET", TOKEN_SECRET);
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", String.valueOf(HSTS_MAX_AGE));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ZEEBE_ENABLED", String.valueOf(true));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ZEEBE_NAME", CUSTOM_ZEEBE_RECORD_PREFIX);
    environmentVariablesExtension.set(
      "CAMUNDA_OPTIMIZE_ZEEBE_PARTITION_COUNT",
      String.valueOf(CUSTOM_ZEEBE_PARTITION_COUNT)
    );
    environmentVariablesExtension.set(
      "CAMUNDA_OPTIMIZE_ZEEBE_MAX_IMPORT_PAGE_SIZE",
      String.valueOf(CUSTOM_ZEEBE_IMPORT_PAGE_SIZE)
    );
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_USERNAME", CUSTOM_ES_USERNAME);
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_PASSWORD", CUSTOM_ES_PASSWORD);
    environmentVariablesExtension.set(
      "CAMUNDA_OPTIMIZE_ELASTICSEARCH_SSL_ENABLED",
      String.valueOf(CUSTOM_ES_SSL_ENABLED)
    );
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_SHARING_ENABLED", String.valueOf(CUSTOM_SHARING_ENABLED));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_UI_LOGOUT_HIDDEN", String.valueOf(CUSTOM_UI_LOGOUT_HIDDEN));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_DATA_ARCHIVE_ENABLED", String.valueOf(false));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTP", String.valueOf(CUSTOM_CONTAINER_HTTP_PORT));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTPS", String.valueOf(CUSTOM_CONTAINER_HTTPS_PORT));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_CONTAINER_KEYSTORE_PASSWORD", CUSTOM_CONTAINER_KEYSTORE_PASSWORD);
    environmentVariablesExtension.set(
      "CAMUNDA_OPTIMIZE_CONTAINER_STATUS_CONNECTIONS_MAX",
      String.valueOf(CUSTOM_CONTAINER_MAX_STATUS_CONNECTIONS)
    );
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_BACKUP_REPOSITORY_NAME", CUSTOM_REPOSITORY_NAME);

    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePropertiesFromSystemVariables() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFE_MIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("PACKAGE_2", CUSTOM_PACKAGE_2);
    System.setProperty("PACKAGE_3", CUSTOM_PACKAGE_3);
    System.setProperty("OPTIMIZE_API_ACCESS_TOKEN", API_SECRET);
    System.setProperty("ACCESS_URL", ACCESS_URL);
    System.setProperty("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS", CUSTOM_EVENT_BASED_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_USER_IDS", CUSTOM_SUPER_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_GROUP_IDS", CUSTOM_SUPER_GROUP_IDS);
    System.setProperty("CAMUNDA_OPTIMIZE_ENTERPRISE", String.valueOf(false));
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED", String.valueOf(true));
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET", TOKEN_SECRET);
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", String.valueOf(HSTS_MAX_AGE));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_ENABLED", String.valueOf(true));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_NAME", CUSTOM_ZEEBE_RECORD_PREFIX);
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_PARTITION_COUNT", String.valueOf(CUSTOM_ZEEBE_PARTITION_COUNT));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_MAX_IMPORT_PAGE_SIZE", String.valueOf(CUSTOM_ZEEBE_IMPORT_PAGE_SIZE));
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_USERNAME", CUSTOM_ES_USERNAME);
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_PASSWORD", CUSTOM_ES_PASSWORD);
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SSL_ENABLED", String.valueOf(CUSTOM_ES_SSL_ENABLED));
    System.setProperty("CAMUNDA_OPTIMIZE_SHARING_ENABLED", String.valueOf(CUSTOM_SHARING_ENABLED));
    System.setProperty("CAMUNDA_OPTIMIZE_UI_LOGOUT_HIDDEN", String.valueOf(CUSTOM_UI_LOGOUT_HIDDEN));
    System.setProperty("CAMUNDA_OPTIMIZE_DATA_ARCHIVE_ENABLED", String.valueOf(false));
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTP", String.valueOf(CUSTOM_CONTAINER_HTTP_PORT));
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTPS", String.valueOf(CUSTOM_CONTAINER_HTTPS_PORT));
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_KEYSTORE_PASSWORD", CUSTOM_CONTAINER_KEYSTORE_PASSWORD);
    System.setProperty(
      "CAMUNDA_OPTIMIZE_CONTAINER_STATUS_CONNECTIONS_MAX",
      String.valueOf(CUSTOM_CONTAINER_MAX_STATUS_CONNECTIONS)
    );
    System.setProperty("CAMUNDA_OPTIMIZE_BACKUP_REPOSITORY_NAME", CUSTOM_REPOSITORY_NAME);

    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePropertiesFromSystemVariablesWinOverEnvironmentVariables() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    environmentVariablesExtension.set("AUTH_TOKEN_LIFEMIN", "wrong");
    environmentVariablesExtension.set("IMPORT_ENABLED_1", "wrong");
    environmentVariablesExtension.set("IMPORT_ENABLED_2", "wrong");
    environmentVariablesExtension.set("ES_HOST_1", "wrong");
    environmentVariablesExtension.set("ES_PORT_1", "wrong");
    environmentVariablesExtension.set("ES_HOST_2", "wrong");
    environmentVariablesExtension.set("ES_PORT_2", "wrong");
    environmentVariablesExtension.set("PACKAGE_2", "wrong");
    environmentVariablesExtension.set("PACKAGE_3", "wrong");
    environmentVariablesExtension.set("SECRET", "wrong");
    environmentVariablesExtension.set("ACCESS_URL", "wrong");
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ENTERPRISE", String.valueOf(true));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED", String.valueOf(false));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET", "wrong");
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", String.valueOf(HSTS_MAX_AGE));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ZEEBE_ENABLED", String.valueOf(false));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ZEEBE_NAME", "wrong");
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ZEEBE_PARTITION_COUNT", String.valueOf(CUSTOM_ZEEBE_PARTITION_COUNT + 1));
    environmentVariablesExtension.set(
      "CAMUNDA_OPTIMIZE_ZEEBE_MAX_IMPORT_PAGE_SIZE",
      String.valueOf(CUSTOM_ZEEBE_IMPORT_PAGE_SIZE + 1)
    );
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_USERNAME", "wrong");
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_PASSWORD", "wrong");
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SSL_ENABLED", String.valueOf(!CUSTOM_ES_SSL_ENABLED));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_SHARING_ENABLED", String.valueOf(!CUSTOM_SHARING_ENABLED));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_UI_LOGOUT_HIDDEN", String.valueOf(!CUSTOM_UI_LOGOUT_HIDDEN));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_DATA_ARCHIVE_ENABLED", String.valueOf(true));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_BACKUP_REPOSITORY_NAME", "wrong");
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTPS", String.valueOf(1233));
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_CONTAINER_KEYSTORE_LOCATION", "envVarKeystore.jks");
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_CONTAINER_KEYSTORE_PASSWORD", "envVarPassword");
    environmentVariablesExtension.set("CAMUNDA_OPTIMIZE_CONTAINER_STATUS_CONNECTIONS_MAX", String.valueOf(15));
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFE_MIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("PACKAGE_2", CUSTOM_PACKAGE_2);
    System.setProperty("PACKAGE_3", CUSTOM_PACKAGE_3);
    System.setProperty("OPTIMIZE_API_ACCESS_TOKEN", API_SECRET);
    System.setProperty("ACCESS_URL", ACCESS_URL);
    System.setProperty("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS", CUSTOM_EVENT_BASED_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_USER_IDS", CUSTOM_SUPER_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_GROUP_IDS", CUSTOM_SUPER_GROUP_IDS);
    System.setProperty("CAMUNDA_OPTIMIZE_ENTERPRISE", String.valueOf(false));
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED", String.valueOf(true));
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET", TOKEN_SECRET);
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", String.valueOf(HSTS_MAX_AGE));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_ENABLED", String.valueOf(true));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_NAME", CUSTOM_ZEEBE_RECORD_PREFIX);
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_PARTITION_COUNT", String.valueOf(CUSTOM_ZEEBE_PARTITION_COUNT));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_MAX_IMPORT_PAGE_SIZE", String.valueOf(CUSTOM_ZEEBE_IMPORT_PAGE_SIZE));
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_USERNAME", CUSTOM_ES_USERNAME);
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_PASSWORD", CUSTOM_ES_PASSWORD);
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SSL_ENABLED", String.valueOf(CUSTOM_ES_SSL_ENABLED));
    System.setProperty("CAMUNDA_OPTIMIZE_SHARING_ENABLED", String.valueOf(CUSTOM_SHARING_ENABLED));
    System.setProperty("CAMUNDA_OPTIMIZE_UI_LOGOUT_HIDDEN", String.valueOf(CUSTOM_UI_LOGOUT_HIDDEN));
    System.setProperty("CAMUNDA_OPTIMIZE_DATA_ARCHIVE_ENABLED", String.valueOf(false));
    System.setProperty("CAMUNDA_OPTIMIZE_BACKUP_REPOSITORY_NAME", CUSTOM_REPOSITORY_NAME);
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTP", String.valueOf(CUSTOM_CONTAINER_HTTP_PORT));
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTPS", String.valueOf(CUSTOM_CONTAINER_HTTPS_PORT));
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_KEYSTORE_PASSWORD", CUSTOM_CONTAINER_KEYSTORE_PASSWORD);
    System.setProperty(
      "CAMUNDA_OPTIMIZE_CONTAINER_STATUS_CONNECTIONS_MAX",
      String.valueOf(CUSTOM_CONTAINER_MAX_STATUS_CONNECTIONS)
    );

    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void resolvePlaceholderDefaultValues() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-default-value-test-config.yaml"};
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatPlaceholderDefaultValuesAreResolved(underTest);
  }

  @Test
  public void resolveSetPropertiesWinOverDefaultValue() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-default-value-test-config.yaml"};
    System.setProperty("AUTH_TOKEN_LIFEMIN", String.valueOf(CUSTOM_AUTH_TOKEN_LIFE_MIN));
    System.setProperty("IMPORT_ENABLED_1", String.valueOf(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED));
    System.setProperty("IMPORT_ENABLED_2", String.valueOf(CUSTOM_SECOND_ENGINE_IMPORT_ENABLED));
    System.setProperty("ES_HOST_1", CUSTOM_FIRST_ES_HOST);
    System.setProperty("ES_PORT_1", String.valueOf(CUSTOM_FIRST_ES_PORT));
    System.setProperty("ES_HOST_2", CUSTOM_SECOND_ES_HOST);
    System.setProperty("ES_PORT_2", String.valueOf(CUSTOM_SECOND_ES_PORT));
    System.setProperty("PACKAGE_2", CUSTOM_PACKAGE_2);
    System.setProperty("PACKAGE_3", CUSTOM_PACKAGE_3);
    System.setProperty("OPTIMIZE_API_ACCESS_TOKEN", API_SECRET);
    System.setProperty("ACCESS_URL", ACCESS_URL);
    System.setProperty("OPTIMIZE_EVENT_BASED_PROCESSES_USER_IDS", CUSTOM_EVENT_BASED_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_USER_IDS", CUSTOM_SUPER_USER_IDS);
    System.setProperty("OPTIMIZE_SUPER_GROUP_IDS", CUSTOM_SUPER_GROUP_IDS);
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_AUTH_COOKIE_SAME_SITE_ENABLED", String.valueOf(true));
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_AUTH_TOKEN_SECRET", TOKEN_SECRET);
    System.setProperty("CAMUNDA_OPTIMIZE_SECURITY_RESPONSE_HEADERS_HSTS_MAX_AGE", String.valueOf(HSTS_MAX_AGE));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_ENABLED", String.valueOf(true));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_NAME", CUSTOM_ZEEBE_RECORD_PREFIX);
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_PARTITION_COUNT", String.valueOf(CUSTOM_ZEEBE_PARTITION_COUNT));
    System.setProperty("CAMUNDA_OPTIMIZE_ZEEBE_MAX_IMPORT_PAGE_SIZE", String.valueOf(CUSTOM_ZEEBE_IMPORT_PAGE_SIZE));
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_USERNAME", CUSTOM_ES_USERNAME);
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SECURITY_PASSWORD", CUSTOM_ES_PASSWORD);
    System.setProperty("CAMUNDA_OPTIMIZE_ELASTICSEARCH_SSL_ENABLED", String.valueOf(CUSTOM_ES_SSL_ENABLED));
    System.setProperty("CAMUNDA_OPTIMIZE_SHARING_ENABLED", String.valueOf(CUSTOM_SHARING_ENABLED));
    System.setProperty("CAMUNDA_OPTIMIZE_UI_LOGOUT_HIDDEN", String.valueOf(CUSTOM_UI_LOGOUT_HIDDEN));
    System.setProperty("CAMUNDA_OPTIMIZE_DATA_ARCHIVE_ENABLED", String.valueOf(false));
    System.setProperty("CAMUNDA_OPTIMIZE_BACKUP_REPOSITORY_NAME", CUSTOM_REPOSITORY_NAME);
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTP", String.valueOf(CUSTOM_CONTAINER_HTTP_PORT));
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_PORTS_HTTPS", String.valueOf(CUSTOM_CONTAINER_HTTPS_PORT));
    System.setProperty("CAMUNDA_OPTIMIZE_CONTAINER_KEYSTORE_PASSWORD", CUSTOM_CONTAINER_KEYSTORE_PASSWORD);
    System.setProperty(
      "CAMUNDA_OPTIMIZE_CONTAINER_STATUS_CONNECTIONS_MAX",
      String.valueOf(CUSTOM_CONTAINER_MAX_STATUS_CONNECTIONS)
    );
    final ConfigurationService underTest = createConfiguration(locations);

    // then
    assertThatVariablePlaceHoldersAreResolved(underTest);
  }

  @Test
  public void failOnMissingSystemOrEnvironmentVariableAndNoDefaultValue() {
    // when
    final String[] locations = {defaultConfigFile(), "environment-variable-test-config.yaml"};
    OptimizeConfigurationException configurationException = null;
    try {
      final ConfigurationService underTest = createConfiguration(locations);
    } catch (OptimizeConfigurationException e) {
      configurationException = e;
    }
    // then
    assertThat(configurationException).isNotNull();
    assertThat(configurationException.getMessage()).contains("Could not resolve system/environment variable");
  }

  @Test
  public void testOverride() {
    String[] locations = {defaultConfigFile(), "environment-config.yaml", "override-test-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);
    assertThat(underTest.getAuthConfiguration().getTokenLifeTimeMinutes()).isEqualTo(10);
  }

  @Test
  public void testAllFieldsAreRead() throws Exception {
    String[] locations = {defaultConfigFile(), "environment-config.yaml", "override-test-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);

    Method[] allMethods = ConfigurationService.class.getMethods();
    for (Method method : allMethods) {
      boolean isGetter = method.getName().startsWith("get") || method.getName().startsWith("is");
      if (isGetter && method.getParameterCount() == 0) {
        Object invoke = method.invoke(underTest);
        assertThat(invoke).withFailMessage("Method " + method.getName() + " returned null").isNotNull();
      }
    }
  }

  @Test
  public void testCutTrailingSlash() {
    // given
    String[] locations = {defaultConfigFile(), "override-engine-config.yaml"};
    ConfigurationService underTest = createConfiguration(locations);

    // when
    String resultUrl =
      underTest.getConfiguredEngines().get("myAwesomeEngine").getWebapps().getEndpoint();

    // then
    assertThat(resultUrl.endsWith("/")).isFalse();
  }

  private ConfigurationService createConfiguration(final String[] locations) {
    return ConfigurationServiceBuilder.createConfiguration().loadConfigurationFrom(locations).build();
  }

  private String defaultConfigFile() {
    return "service-config.yaml";
  }

  private void assertThatPlaceholderDefaultValuesAreResolved(final ConfigurationService underTest) {
    assertThat(underTest.getAuthConfiguration().getTokenLifeTimeMinutes()).isEqualTo(DEFAULT_AUTH_TOKEN_LIFE_MIN);
    assertThat(
      underTest.getConfiguredEngines().values().stream().map(EngineConfiguration::isImportEnabled).collect(toList()))
      .contains(DEFAULT_FIRST_ENGINE_IMPORT_ENABLED, DEFAULT_SECOND_ENGINE_IMPORT_ENABLED);
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHost)
        .collect(toList()))
      .contains(DEFAULT_FIRST_ES_HOST, DEFAULT_SECOND_ES_HOST);
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHttpPort)
        .collect(toList()))
      .contains(DEFAULT_FIRST_ES_PORT, DEFAULT_SECOND_ES_PORT);
    assertThat(underTest.getVariableImportPluginBasePackages()).contains("1", DEFAULT_PACKAGE_2, DEFAULT_PACKAGE_3);
    assertThat(underTest.getEventBasedProcessConfiguration().getAuthorizedUserIds()).isEmpty();
    assertThat(underTest.getEventBasedProcessConfiguration().getAuthorizedGroupIds()).isEmpty();
    assertThat(underTest.getOptimizeApiConfiguration().getAccessToken()).isNull();
    assertThat(underTest.getContainerAccessUrl()).isNotPresent();
    assertThat(underTest.getSecurityConfiguration().getLicense().isEnterprise()).isFalse();
    assertThat(underTest.getSecurityConfiguration().getAuth().getCookieConfiguration().isSameSiteFlagEnabled())
      .isTrue();
    assertThat(underTest.getSecurityConfiguration().getAuth().getTokenSecret()).isEmpty();
    assertThat(underTest.getSecurityConfiguration().getResponseHeaders().getHttpStrictTransportSecurityMaxAge())
      .isEqualTo(63072000);
    assertThat(underTest.getConfiguredZeebe().isEnabled()).isFalse();
    assertThat(underTest.getConfiguredZeebe().getName()).isEqualTo("zeebe-record");
    assertThat(underTest.getConfiguredZeebe().getPartitionCount()).isEqualTo(1);
    assertThat(underTest.getConfiguredZeebe().getMaxImportPageSize()).isEqualTo(10000);
    assertThat(underTest.getElasticsearchSecurityUsername()).isNull();
    assertThat(underTest.getElasticsearchSecurityPassword()).isNull();
    assertThat(underTest.getElasticsearchSecuritySSLCertificate()).isNull();
    assertThat(underTest.getElasticsearchSecuritySSLCertificateAuthorities()).isEmpty();
    assertThat(underTest.getElasticsearchSecuritySSLEnabled()).isFalse();
    assertThat(underTest.getSharingEnabled()).isTrue();
    assertThat(underTest.getUiConfiguration().isLogoutHidden()).isFalse();
  }

  private void assertThatVariablePlaceHoldersAreResolved(final ConfigurationService underTest) {
    assertThat(underTest.getAuthConfiguration().getTokenLifeTimeMinutes()).isEqualTo(CUSTOM_AUTH_TOKEN_LIFE_MIN);
    assertThat(
      underTest.getConfiguredEngines().values().stream().map(EngineConfiguration::isImportEnabled).collect(toList()))
      .contains(CUSTOM_FIRST_ENGINE_IMPORT_ENABLED, CUSTOM_SECOND_ENGINE_IMPORT_ENABLED);
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHost)
        .collect(toList()))
      .contains(CUSTOM_FIRST_ES_HOST, CUSTOM_SECOND_ES_HOST);
    assertThat(
      underTest.getElasticsearchConnectionNodes()
        .stream()
        .map(ElasticsearchConnectionNodeConfiguration::getHttpPort)
        .collect(toList()))
      .contains(CUSTOM_FIRST_ES_PORT, CUSTOM_SECOND_ES_PORT);
    assertThat(
      underTest.getVariableImportPluginBasePackages())
      .contains("1", CUSTOM_PACKAGE_2, CUSTOM_PACKAGE_3);
    assertThat(
      underTest.getEventBasedProcessConfiguration().getAuthorizedUserIds()).isEqualTo(ImmutableList.of("demo", "kermit"
    ));
    assertThat(underTest.getOptimizeApiConfiguration().getAccessToken()).isEqualTo(API_SECRET);
    assertThat(underTest.getContainerAccessUrl()).isPresent().get().isEqualTo(ACCESS_URL);
    assertThat(underTest.getAuthConfiguration().getSuperUserIds())
      .isEqualTo(ImmutableList.of("demo", "kermit"));
    assertThat(underTest.getSecurityConfiguration().getAuth().getCookieConfiguration().isSameSiteFlagEnabled())
      .isTrue();
    assertThat(underTest.getSecurityConfiguration().getAuth().getTokenSecret()).isPresent()
      .get()
      .isEqualTo(TOKEN_SECRET);
    assertThat(underTest.getSecurityConfiguration().getResponseHeaders().getHttpStrictTransportSecurityMaxAge())
      .isEqualTo(HSTS_MAX_AGE);
    assertThat(underTest.getConfiguredZeebe().isEnabled()).isEqualTo(CUSTOM_ZEEBE_ENABLED);
    assertThat(underTest.getConfiguredZeebe().getName()).isEqualTo(CUSTOM_ZEEBE_RECORD_PREFIX);
    assertThat(underTest.getConfiguredZeebe().getPartitionCount()).isEqualTo(CUSTOM_ZEEBE_PARTITION_COUNT);
    assertThat(underTest.getConfiguredZeebe().getMaxImportPageSize()).isEqualTo(CUSTOM_ZEEBE_IMPORT_PAGE_SIZE);
    assertThat(underTest.getElasticsearchSecurityUsername()).isEqualTo(CUSTOM_ES_USERNAME);
    assertThat(underTest.getElasticsearchSecurityPassword()).isEqualTo(CUSTOM_ES_PASSWORD);
    assertThat(underTest.getElasticsearchSecuritySSLEnabled()).isEqualTo(CUSTOM_ES_SSL_ENABLED);
    assertThat(underTest.getSharingEnabled()).isEqualTo(CUSTOM_SHARING_ENABLED);
    assertThat(underTest.getUiConfiguration().isLogoutHidden()).isEqualTo(CUSTOM_UI_LOGOUT_HIDDEN);
    assertThat(underTest.getDataArchiveConfiguration().isEnabled()).isFalse();

    assertThat(underTest.getContainerHttpPort()).isPresent().get().isEqualTo(CUSTOM_CONTAINER_HTTP_PORT);
    assertThat(underTest.getContainerHttpsPort()).isEqualTo(CUSTOM_CONTAINER_HTTPS_PORT);
    assertThat(underTest.getContainerKeystorePassword()).isEqualTo(CUSTOM_CONTAINER_KEYSTORE_PASSWORD);
    assertThat(underTest.getMaxStatusConnections()).isEqualTo(CUSTOM_CONTAINER_MAX_STATUS_CONNECTIONS);
  }

}