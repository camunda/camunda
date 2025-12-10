/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.utility.MountableFile;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      OpenSearchConnector.class,
      TasklistPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    })
@ContextConfiguration(initializers = {OpenSearchConnectorSSLAuthIT.ElasticsearchStarter.class})
@Profile("opensearch-test")
@Disabled
public class OpenSearchConnectorSSLAuthIT extends TasklistIntegrationTest {

  private static final String CERTIFICATE_RESOURCE_PATH = "certs/elastic-stack-ca.p12";

  static OpenSearchContainer opensearch =
      (OpenSearchContainer)
          new OpenSearchContainer("opensearchproject/opensearch:2.17.0")
              // .withSecurityEnabled() // this should be uncommented to test SSL
              .withCopyFileToContainer(
                  MountableFile.forClasspathResource(CERTIFICATE_RESOURCE_PATH),
                  "/usr/share/opensearch/config/certs/opensearch-ca.p12")
              .withEnv(
                  Map.of(
                      "plugins.security.allow_unsafe_democertificates",
                      "true",
                      "plugins.security.ssl.http.enabled",
                      "true",
                      "plugins.security.ssl.http.keystore_type",
                      "PKCS12",
                      "plugins.security.ssl.http.keystore_filepath",
                      "opensearch.keystore",
                      "plugins.security.ssl.transport.keystore_type",
                      "PKCS12",
                      "plugins.security.ssl.transport.keystore_filepath",
                      "opensearch.keystore"));

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  @BeforeAll
  static void beforeAll() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @Disabled("SSL configuration not working - need investigation")
  @Test
  public void canConnect() {
    assertThat(openSearchClient).isNotNull();
  }

  static class ElasticsearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      opensearch.start();
      final boolean isSecurityEnabled = opensearch.isSecurityEnabled();
      final String protocol = isSecurityEnabled ? "https" : "http";
      final String osUrl =
          String.format(
              "%s://%s:%d/", protocol, opensearch.getHost(), opensearch.getFirstMappedPort());

      TestPropertyValues.of(
              "camunda.data.secondary-storage.type=opensearch",
              "camunda.database.url=" + osUrl,
              "camunda.data.secondary-storage.opensearch.url=" + osUrl,
              "camunda.database.opensearch.username=" + opensearch.getUsername(),
              "camunda.database.opensearch.password=" + opensearch.getPassword(),
              "camunda.data.secondary-storage.opensearch.ssl.enabled=" + isSecurityEnabled,
              "camunda.data.secondary-storage.opensearch.ssl.self-signed=true",
              "camunda.data.secondary-storage.opensearch.ssl.verify-hostname=false",
              "camunda.database.opensearch.ssl.enabled=" + isSecurityEnabled,
              "camunda.database.opensearch.ssl.self-signed=true",
              "camunda.database.opensearch.ssl.verify-hostname=false",
              "camunda.data.secondary-storage.opensearch.security.certificate-path="
                  + getClass().getClassLoader().getResource(CERTIFICATE_RESOURCE_PATH).getPath())
          .applyTo(applicationContext.getEnvironment());
    }
  }
}
