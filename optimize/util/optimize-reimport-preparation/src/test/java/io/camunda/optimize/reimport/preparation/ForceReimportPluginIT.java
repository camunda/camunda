/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.reimport.preparation;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createConfigurationFromLocations;
import static jakarta.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import io.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Slf4j
public class ForceReimportPluginIT extends AbstractEventProcessIT {

  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void fixedElasticsearchCustomHeaderPluginsAreUsedDuringForcedReimport() {
    // given
    final String basePackage = "io.camunda.optimize.testplugin.elasticsearch.authorization.fixed";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    final ClientAndServer dbMockServer = useAndGetDbMockServer();

    // when
    forceReimportOfEngineData();

    // then
    dbMockServer.verify(request().withHeader(new Header("Authorization", "Bearer fixedToken")));
  }

  @Test
  public void dynamicElasticsearchCustomHeaderPluginsAreUsedDuringForcedReimport() {
    // given
    final String basePackage = "io.camunda.optimize.testplugin.elasticsearch.authorization.dynamic";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    final ClientAndServer dbMockServer = useAndGetDbMockServer();

    // when
    forceReimportOfEngineData();
    // clear the version validation request the client does on first use, which bypasses our plugins
    // see RestHighLevelClient#versionValidationFuture
    dbMockServer.clear(request("/").withMethod(GET));

    // then
    final RequestDefinition[] allRequests = dbMockServer.retrieveRecordedRequests(null);
    assertThat(allRequests).hasSizeGreaterThan(1);
    IntStream.range(0, allRequests.length)
        .forEach(
            integerSuffix ->
                dbMockServer.verify(
                    request()
                        .withHeader(
                            new Header("Authorization", "Bearer dynamicToken_" + integerSuffix)),
                    VerificationTimes.once()));
  }

  @Test
  public void multipleElasticsearchCustomHeaderPluginsAreUsedDuringForcedReimport() {
    // given
    final String[] basePackages = {
        "io.camunda.optimize.testplugin.elasticsearch.authorization.dynamic",
        "io.camunda.optimize.testplugin.elasticsearch.custom"
    };
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackages);
    final ClientAndServer dbMockServer = useAndGetDbMockServer();

    // when
    forceReimportOfEngineData();

    // then
    dbMockServer.verify(
        request()
            .withHeaders(
                new Header("Authorization", "Bearer dynamicToken_0"),
                new Header("CustomHeader", "customValue")),
        // The first request is to check the ES version during client creation, the second is
        // being done when fetching the ES version to verify if we need to turn on the compatibility
        // mode.
        VerificationTimes.exactly(2));
  }

  private void forceReimportOfEngineData() {
    ReimportPreparation.performReimport(configurationService);
  }

  private void addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(
      final String... basePackages) {
    final List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setElasticsearchCustomHeaderPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @Import(io.camunda.optimize.Main.class)
  @TestConfiguration
  public class Configuration {

    @Bean
    @Primary
    public ConfigurationService configurationService() {
      final ConfigurationService configurationService =
          createConfigurationFromLocations("service-config.yaml", "it/it-config.yaml");
      return configurationService;
    }
  }
}
