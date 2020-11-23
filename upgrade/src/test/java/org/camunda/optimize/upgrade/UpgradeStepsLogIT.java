/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.util.configuration.elasticsearch.ElasticsearchConnectionNodeConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.upgrade.es.index.UpdateLogEntryIndex;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.indexes.UserTestIndex;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import org.camunda.optimize.upgrade.service.UpgradeStepLogService;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;
import org.camunda.optimize.upgrade.steps.UpgradeStepType;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.util.UpgradeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createDefaultConfiguration;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.es.SchemaUpgradeClientFactory.createSchemaUpgradeClient;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

public class UpgradeStepsLogIT extends AbstractUpgradeIT {

  private static final IndexMappingCreator TEST_INDEX_V1 = new UserTestIndex(1);
  private static final IndexMappingCreator TEST_INDEX_V2 = new UserTestIndex(2);

  private static final String FROM_VERSION = "2.6.0";
  private static final String TO_VERSION = "2.7.0";

  @RegisterExtension
  LogCapturer logs = LogCapturer.create().captureForType(UpgradeProcedure.class);

  private ClientAndServer esMockServer;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    this.configurationService = createDefaultConfiguration();
    final ElasticsearchConnectionNodeConfiguration elasticConfig =
      this.configurationService.getFirstElasticsearchConnectionNode();

    this.esMockServer = createElasticMock(elasticConfig);
    elasticConfig.setHost(MockServerUtil.MOCKSERVER_HOST);
    elasticConfig.setHttpPort(IntegrationTestConfigurationUtil.getElasticsearchMockServerPort());

    this.upgradeDependencies =
      UpgradeUtil.createUpgradeDependenciesWithAConfigurationService(this.configurationService);
    this.objectMapper = upgradeDependencies.getObjectMapper();
    this.prefixAwareClient = upgradeDependencies.getEsClient();
    this.indexNameService = upgradeDependencies.getIndexNameService();
    this.metadataService = upgradeDependencies.getMetadataService();
    this.upgradeProcedure = new UpgradeProcedure(
      prefixAwareClient,
      new UpgradeValidationService(),
      createSchemaUpgradeClient(upgradeDependencies),
      new UpgradeStepLogService()
    );

    cleanAllDataFromElasticsearch();
    createEmptyEnvConfig();
    initSchema(Lists.newArrayList(METADATA_INDEX));
    setMetadataVersion(FROM_VERSION);
  }

  @AfterEach
  @Override
  public void after() throws Exception {
    super.after();
    this.esMockServer.close();
  }

  @Test
  public void singleUpdateStepIsLogged() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final Optional<UpgradeStepLogEntryDto> updateLogEntries = getDocumentsOfIndexByIdAs(
      UpdateLogEntryIndex.INDEX_NAME,
      TO_VERSION + "_" + UpgradeStepType.SCHEMA_CREATE_INDEX.toString() + "_001",
      UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .isPresent()
      .get()
      .isEqualTo(
        UpgradeStepLogEntryDto.builder()
          .indexName(TEST_INDEX_V1.getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(UpgradeStepType.SCHEMA_CREATE_INDEX)
          .appliedDate(frozenDate.toInstant())
          .build()
      );
  }

  @Test
  public void multipleUpdateStepAreLogged() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_V2))
        .build();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    final List<UpgradeStepLogEntryDto> updateLogEntries = getAllDocumentsOfIndexAs(
      UpdateLogEntryIndex.INDEX_NAME, UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .containsExactlyInAnyOrder(
        UpgradeStepLogEntryDto.builder()
          .indexName(TEST_INDEX_V1.getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(UpgradeStepType.SCHEMA_CREATE_INDEX)
          .appliedDate(frozenDate.toInstant())
          .build(),
        UpgradeStepLogEntryDto.builder()
          .indexName(TEST_INDEX_V2.getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(2)
          .stepType(UpgradeStepType.SCHEMA_UPDATE_INDEX)
          .appliedDate(frozenDate.toInstant())
          .build()
      );
  }

  @Test
  public void onlySuccessfulUpdateStepsAreLogged() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_V2))
        .build();
    final HttpRequest indexDeleteRequest = createIndexDeleteRequestMatcher(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V1)
    );
    esMockServer
      .when(indexDeleteRequest, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));

    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan)).isInstanceOf(UpgradeRuntimeException.class);
    esMockServer.verify(indexDeleteRequest, exactly(1));

    // then only the successful first step is logged
    logs.assertContains("Starting step 1/2: CreateIndexStep on index: " + TEST_INDEX_V1.getIndexName());
    logs.assertContains("Successfully finished step 1/2: CreateIndexStep on index: " + TEST_INDEX_V1.getIndexName());
    logs.assertContains("The upgrade will be aborted. Please investigate the cause and retry it..");
    final List<UpgradeStepLogEntryDto> updateLogEntries = getAllDocumentsOfIndexAs(
      UpdateLogEntryIndex.INDEX_NAME, UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .containsExactlyInAnyOrder(
        UpgradeStepLogEntryDto.builder()
          .indexName(TEST_INDEX_V1.getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(UpgradeStepType.SCHEMA_CREATE_INDEX)
          .appliedDate(frozenDate.toInstant())
          .build()
      );
  }

  @Test
  public void upgradeIsResumedWhenUpgradeStepRequestFails() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_V2))
        .build();
    final HttpRequest indexDeleteRequest = createIndexDeleteRequestMatcher(
      indexNameService.getOptimizeIndexNameWithVersion(TEST_INDEX_V1)
    );
    esMockServer
      .when(indexDeleteRequest, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));

    // the upgrade is executed and failed
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan)).isInstanceOf(UpgradeRuntimeException.class);
    esMockServer.verify(indexDeleteRequest, exactly(1));

    // when it is retried
    final OffsetDateTime frozenDate2 = DateCreationFreezer.dateFreezer()
      .dateToFreeze(frozenDate.plus(10, ChronoUnit.SECONDS))
      .freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds and the whole log is persisted
    logs.assertContains(
      "Skipping Step 1/2: CreateIndexStep on index: users as it was found to be previously completed " +
        "already at: " + frozenDate.toInstant().toString() + "."
    );
    logs.assertContains("Starting step 2/2: UpdateIndexStep on index: " + TEST_INDEX_V1.getIndexName());
    logs.assertContains("Successfully finished step 2/2: UpdateIndexStep on index: " + TEST_INDEX_V1.getIndexName());
    final List<UpgradeStepLogEntryDto> updateLogEntries = getAllDocumentsOfIndexAs(
      UpdateLogEntryIndex.INDEX_NAME, UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .containsExactlyInAnyOrder(
        UpgradeStepLogEntryDto.builder()
          .indexName(TEST_INDEX_V1.getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(UpgradeStepType.SCHEMA_CREATE_INDEX)
          .appliedDate(frozenDate.toInstant())
          .build(),
        UpgradeStepLogEntryDto.builder()
          .indexName(TEST_INDEX_V2.getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(2)
          .stepType(UpgradeStepType.SCHEMA_UPDATE_INDEX)
          .appliedDate(frozenDate2.toInstant())
          .build()
      );
  }

  @Test
  public void upgradeIsResumedWhenUpgradeLogUpdateFails() {
    // given
    final OffsetDateTime frozenDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(buildCreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(buildUpdateIndexStep(TEST_INDEX_V2))
        .build();
    final HttpRequest stepOneLogUpsertRequest = request()
      .withPath(
        "/" + indexNameService.getOptimizeIndexAliasForIndex(UpdateLogEntryIndex.INDEX_NAME) + "/_update/"
          + TO_VERSION + "_" + UpgradeStepType.SCHEMA_CREATE_INDEX.toString() + "_001"
      )
      .withMethod(POST);
    esMockServer
      .when(stepOneLogUpsertRequest, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));

    // the upgrade is executed and failed
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan)).isInstanceOf(UpgradeRuntimeException.class);
    esMockServer.verify(stepOneLogUpsertRequest, exactly(1));

    // when it is retried
    final OffsetDateTime frozenDate2 = DateCreationFreezer.dateFreezer()
      .dateToFreeze(frozenDate.plus(10, ChronoUnit.SECONDS))
      .freezeDateAndReturn();
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it succeeds and the whole log is persisted
    logs.assertDoesNotContain("Skipping Step 1/2: CreateIndexStep on index");
    logs.assertContains("Starting step 1/2: CreateIndexStep on index: " + TEST_INDEX_V1.getIndexName());
    logs.assertContains("Successfully finished step 1/2: CreateIndexStep on index: " + TEST_INDEX_V1.getIndexName());
    logs.assertContains("Starting step 2/2: UpdateIndexStep on index: " + TEST_INDEX_V1.getIndexName());
    logs.assertContains("Successfully finished step 2/2: UpdateIndexStep on index: " + TEST_INDEX_V1.getIndexName());
    final List<UpgradeStepLogEntryDto> updateLogEntries = getAllDocumentsOfIndexAs(
      UpdateLogEntryIndex.INDEX_NAME, UpgradeStepLogEntryDto.class
    );
    assertThat(updateLogEntries)
      .containsExactlyInAnyOrder(
        UpgradeStepLogEntryDto.builder()
          .indexName(TEST_INDEX_V1.getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(1)
          .stepType(UpgradeStepType.SCHEMA_CREATE_INDEX)
          .appliedDate(frozenDate2.toInstant())
          .build(),
        UpgradeStepLogEntryDto.builder()
          .indexName(TEST_INDEX_V2.getIndexName())
          .optimizeVersion(TO_VERSION)
          .stepNumber(2)
          .stepType(UpgradeStepType.SCHEMA_UPDATE_INDEX)
          .appliedDate(frozenDate2.toInstant())
          .build()
      );
  }

  private CreateIndexStep buildCreateIndexStep(final IndexMappingCreator index) {
    return new CreateIndexStep(index);
  }

  private UpdateIndexStep buildUpdateIndexStep(final IndexMappingCreator index) {
    return new UpdateIndexStep(index, null);
  }

}
