/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.camunda.operate.property.OperateProperties;
import java.io.IOException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class AbstractElasticsearchConnectorProxyIT {

  @Container
  protected static ElasticsearchContainer elasticsearch =
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                  .withTag(RestClient.class.getPackage().getImplementationVersion()))
          .withEnv("xpack.security.enabled", "false")
          .withEnv("xpack.security.http.ssl.enabled", "false");

  // We can't use field injections from the WireMock or TempDir extensions, as those would run after
  // the DynamicPropertySource method used by SpringBootTest; so we need to manually manage their
  // lifecycle here instead
  protected static final WireMockServer WIRE_MOCK_SERVER =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @BeforeAll
  public static void beforeAll() {
    WIRE_MOCK_SERVER.start();
  }

  @AfterAll
  public static void afterAll() throws IOException {
    WIRE_MOCK_SERVER.stop();
  }

  @DynamicPropertySource
  public static void setWiremockProxyProperties(final DynamicPropertyRegistry registry)
      throws IOException {

    // need to start server here since this is called before any other extensions
    WIRE_MOCK_SERVER.start();
    WIRE_MOCK_SERVER.stubFor(
        WireMock.any(WireMock.anyUrl())
            .willReturn(
                WireMock.aResponse().proxiedFrom("http://" + elasticsearch.getHttpHostAddress())));

    registry.add(OperateProperties.PREFIX + ".elasticsearch.url", WIRE_MOCK_SERVER::baseUrl);
    registry.add(OperateProperties.PREFIX + ".zeebeElasticsearch.url", WIRE_MOCK_SERVER::baseUrl);
  }
}
