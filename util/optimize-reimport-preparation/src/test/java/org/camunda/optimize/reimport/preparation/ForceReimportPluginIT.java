/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.reimport.preparation;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createConfigurationFromLocations;
import static org.mockserver.model.HttpRequest.request;

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
    String basePackage = "org.camunda.optimize.testplugin.elasticsearch.authorization.fixed";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    // when
    forceReimportOfEngineData();

    // then
    esMockServer.verify(request().withHeader(new Header("Authorization", "Bearer fixedToken")));
  }

  @Test
  public void dynamicElasticsearchCustomHeaderPluginsAreUsedDuringForcedReimport() {
    // given
    String basePackage = "org.camunda.optimize.testplugin.elasticsearch.authorization.dynamic";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    // when
    forceReimportOfEngineData();
    // clear the version validation request the client does on first use, which bypasses our plugins
    // see RestHighLevelClient#versionValidationFuture
    esMockServer.clear(request("/").withMethod(GET));

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
  public void multipleElasticsearchCustomHeaderPluginsAreUsedDuringForcedReimport() {
    // given
    String[] basePackages = {
      "org.camunda.optimize.testplugin.elasticsearch.authorization.dynamic",
      "org.camunda.optimize.testplugin.elasticsearch.custom"
    };
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackages);
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();

    // when
    forceReimportOfEngineData();

    // then
    esMockServer.verify(request().withHeaders(
      new Header("Authorization", "Bearer dynamicToken_0"),
      new Header("CustomHeader", "customValue")
    ), VerificationTimes.exactly(2));
  }

  private void forceReimportOfEngineData() {
    ReimportPreparation.performReimport(configurationService);
  }

  private void addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setElasticsearchCustomHeaderPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @Import(org.camunda.optimize.Main.class)
  @TestConfiguration
  public class Configuration {
    @Bean
    @Primary
    public ConfigurationService configurationService() {
      ConfigurationService configurationService = createConfigurationFromLocations(
        "service-config.yaml",
        "it/it-config.yaml"
      );
      return configurationService;
    }
  }

}
