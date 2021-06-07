/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.elasticsearch;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

public class ElasticsearchCustomHeaderSupplierPluginIT extends AbstractIT {

  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void fixedCustomHeadersAddedToElasticsearchRequest() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.elasticsearch.authorization.fixed";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    // when
    statusClient.getStatus();

    // then
    esMockServer.verify(request().withHeader(new Header("Authorization", "Bearer fixedToken")));
  }

  @Test
  public void dynamicCustomHeadersAddedToElasticsearchRequest() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.elasticsearch.authorization.dynamic";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    // when
    statusClient.getStatus();
    statusClient.getStatus();
    statusClient.getStatus();

    // then
    final RequestDefinition[] allRequests = esMockServer.retrieveRecordedRequests(null);
    assertThat(allRequests).hasSizeGreaterThan(1);
    IntStream.range(0, allRequests.length)
      .forEach(integerSuffix -> esMockServer.verify(
        request().withHeader(new Header("Authorization", "Bearer dynamicToken_" + integerSuffix)),
        VerificationTimes.once()
      ));
  }

  @Test
  public void multipleCustomHeadersAddedToElasticsearchRequest() {
    // given
    String[] basePackages = {
      "org.camunda.optimize.testplugin.elasticsearch.authorization.dynamic",
      "org.camunda.optimize.testplugin.elasticsearch.custom"
    };
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackages);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    // when
    statusClient.getStatus();

    // then
    esMockServer.verify(request().withHeaders(
      new Header("Authorization", "Bearer dynamicToken_0"),
      new Header("CustomHeader", "customValue")
    ), VerificationTimes.once());
  }

  private void addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setElasticsearchCustomHeaderPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }

}
