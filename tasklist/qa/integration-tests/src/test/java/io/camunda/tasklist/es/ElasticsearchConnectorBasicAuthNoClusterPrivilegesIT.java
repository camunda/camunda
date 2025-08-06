/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.security.IndicesPrivileges;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class
    },
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(
    initializers = {
      ElasticsearchConnectorBasicAuthNoClusterPrivilegesIT.ElasticsearchStarter.class
    })
public class ElasticsearchConnectorBasicAuthNoClusterPrivilegesIT extends TasklistIntegrationTest {

  private static final String ES_ADMIN_USER = "elastic";
  private static final String ES_ADMIN_PASSWORD = "changeme";
  static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(
              "docker.elastic.co/elasticsearch/elasticsearch:" + SUPPORTED_ELASTICSEARCH_VERSION)
          .withEnv(Map.of("xpack.security.enabled", "true", "ELASTIC_PASSWORD", ES_ADMIN_PASSWORD))
          .withExposedPorts(9200);
  private static final String TASKLIST_ES_USER = "tasklist_user";
  private static final String TASKLIST_ES_PASSWORD = "tasklist_pwd";
  @Autowired RestHighLevelClient tasklistEsClient;

  @Autowired RestHighLevelClient tasklistZeebeEsClient;

  @Autowired private TestRestTemplate testRestTemplate;

  @LocalManagementPort private int managementPort;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @Test
  public void canConnect() {
    assertThat(tasklistEsClient).isNotNull();
    assertThat(tasklistZeebeEsClient).isNotNull();
    final var healthCheck =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/health", Map.class);
    assertThat(healthCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(healthCheck.getBody()).containsEntry("status", "UP");
  }

  static class ElasticsearchStarter
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      elasticsearch.start();

      createElasticsearchRoleAndUser();

      final String elsUrl = String.format("http://%s", elasticsearch.getHttpHostAddress());
      TestPropertyValues.of(
              "camunda.tasklist.elasticsearch.username=" + TASKLIST_ES_USER,
              "camunda.tasklist.elasticsearch.password=" + TASKLIST_ES_PASSWORD,
              "camunda.tasklist.elasticsearch.clusterName=docker-cluster",
              "camunda.tasklist.elasticsearch.url=" + elsUrl,
              "camunda.tasklist.zeebeElasticsearch.url=" + elsUrl,
              "camunda.tasklist.zeebeElasticsearch.username=" + TASKLIST_ES_USER,
              "camunda.tasklist.zeebeElasticsearch.password=" + TASKLIST_ES_PASSWORD,
              "camunda.tasklist.zeebeElasticsearch.clusterName=docker-cluster",
              "camunda.tasklist.zeebeElasticsearch.prefix=zeebe-record",
              "camunda.tasklist.elasticsearch.healthCheckEnabled=false")
          .applyTo(applicationContext.getEnvironment());
    }

    private void createElasticsearchRoleAndUser() {
      try {
        final ElasticsearchClient elasticsearchClient = createAdminElasticsearchClient();
        final String tasklistEsRole = "tasklist_role";
        elasticsearchClient
            .security()
            .putRole(
                role ->
                    role.name(tasklistEsRole)
                        .indices(
                            IndicesPrivileges.of(
                                index ->
                                    index
                                        .names("tasklist*")
                                        .privileges(
                                            "create_index",
                                            "delete_index",
                                            "read",
                                            "write",
                                            "manage",
                                            "manage_ilm")
                                        .allowRestrictedIndices(false)),
                            IndicesPrivileges.of(
                                index -> index.names("zeebe*").privileges("read"))));
        elasticsearchClient
            .security()
            .putUser(
                user ->
                    user.username(TASKLIST_ES_USER)
                        .password(TASKLIST_ES_PASSWORD)
                        .roles(tasklistEsRole));
      } catch (final IOException | URISyntaxException e) {
        fail(e);
      }
    }

    private ElasticsearchClient createAdminElasticsearchClient() throws URISyntaxException {
      final var credentialProvider = new BasicCredentialsProvider();
      credentialProvider.setCredentials(
          AuthScope.ANY, new UsernamePasswordCredentials(ES_ADMIN_USER, ES_ADMIN_PASSWORD));
      final URI uri = new URI("http://" + elasticsearch.getHttpHostAddress());
      return new ElasticsearchClient(
          new RestClientTransport(
              RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
                  .setHttpClientConfigCallback(
                      httpClientBuilder ->
                          httpClientBuilder.setDefaultCredentialsProvider(credentialProvider))
                  .build(),
              new JacksonJsonpMapper()));
    }
  }
}
