/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static org.mockserver.model.HttpRequest.request;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import io.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.Header;
import org.mockserver.model.NottableString;
import org.mockserver.verify.VerificationTimes;

public class UpgradePluginIT extends AbstractUpgradeIT {

  @BeforeEach
  public void setup() {
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void fixedHeaderPluginsAreUsedDuringUpgrade() {
    // given
    String basePackage = "io.camunda.optimize.testplugin.elasticsearch.authorization.fixed";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    setUpUpgradeDependenciesWithConfiguration(configurationService);
    // clear all mock recordings that happen during setup
    dbMockServer.clear(request());

    // given
    performUpgrade();

    // then
    dbMockServer.verify(
        request().withHeader(new Header("Authorization", "Bearer fixedToken")),
        VerificationTimes.atLeast(2));
    // ensure there was no request without the header
    dbMockServer.verify(
        request().withHeader(NottableString.not("Authorization")), VerificationTimes.exactly(0));
  }

  @Test
  public void dynamicCustomHeaderPluginsAreUsedDuringUpgrade() {
    // given
    String basePackage = "io.camunda.optimize.testplugin.elasticsearch.authorization.dynamic";
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackage);
    setUpUpgradeDependenciesWithConfiguration(configurationService);

    // given
    performUpgrade();

    // then
    dbMockServer.verify(
        request().withHeader(new Header("Authorization", "Bearer dynamicToken_0")),
        // The first request is to check the ES version during client creation, the second is
        // being done when fetching the ES version to verify if we need to turn on the compatibility
        // mode.
        VerificationTimes.exactly(2));
    dbMockServer.verify(
        request().withHeader(new Header("Authorization", "Bearer dynamicToken_1")),
        VerificationTimes.once());
    dbMockServer.verify(
        request().withHeader(new Header("Authorization", "Bearer dynamicToken_2")),
        VerificationTimes.once());
  }

  @Test
  public void multipleCustomHeaderPluginsAreUsedDuringUpgrade() {
    // given
    String[] basePackages = {
      "io.camunda.optimize.testplugin.elasticsearch.authorization.dynamic",
      "io.camunda.optimize.testplugin.elasticsearch.custom"
    };
    addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(basePackages);
    setUpUpgradeDependenciesWithConfiguration(configurationService);

    // given
    performUpgrade();

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

  private void performUpgrade() {
    upgradeProcedure.performUpgrade(
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(FROM_VERSION)
            .toVersion(INTERMEDIATE_VERSION)
            .addUpgradeSteps(
                ImmutableList.of(
                    new CreateIndexStep(TEST_INDEX_WITH_TEMPLATE_V1),
                    buildInsertTestIndexDataStep(TEST_INDEX_WITH_TEMPLATE_V1),
                    buildUpdateTestIndexDataStep(TEST_INDEX_WITH_TEMPLATE_V1),
                    new UpdateIndexStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2),
                    buildDeleteTestIndexDataStep(TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2),
                    new DeleteIndexIfExistsStep(
                        TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getIndexName(),
                        TEST_INDEX_WITH_TEMPLATE_UPDATED_MAPPING_V2.getVersion())))
            .build());
  }

  private void addElasticsearchCustomHeaderPluginBasePackagesToConfiguration(
      String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setElasticsearchCustomHeaderPluginBasePackages(basePackagesList);
  }
}
