/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.IndexSettingsBuilder;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.settings.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeCamundaActivityEventActivityInstanceIdIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "3.0.0";

  private static final String PROCESS_START_SUFFIX = "_processInstanceStart";
  private static final String PROCESS_END_SUFFIX = "_processInstanceEnd";
  private static final String TASK_START_SUFFIX = "_start";
  private static final String TASK_END_SUFFIX = "_end";

  // These values are the same as used in the json bulk
  private static final String FIRST_DEFINITION_KEY = "process_8543247f-26d4-4291-8433-e5c77a1e9b4b";
  private static final String SECOND_DEFINITION_KEY = "process_5553247f-26d4-4291-8433-e5c77a1e9b4b";
  private static final String FIRST_INSTANCE_ID = "59715";
  private static final String SECOND_INSTANCE_ID = "00002";
  private static final String OTHER_INSTANCE_ID = "someOtherActivityInstanceId";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      COMBINED_REPORT_INDEX,
      TIMESTAMP_BASED_IMPORT_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);

    createCamundaActivityEventV1IndexForDefinitionKey(FIRST_DEFINITION_KEY);
    createCamundaActivityEventV1IndexForDefinitionKey(SECOND_DEFINITION_KEY);
    upgradeDependencies.getEsClient()
      .getHighLevelClient()
      .indices()
      .refresh(new RefreshRequest(), RequestOptions.DEFAULT);

    executeBulk("steps/camunda_activity_events/30-camunda-activity-events-bulk");
  }

  @SneakyThrows
  @Test
  public void processStartAndEndActivityInstanceIdsAreMigrated() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();
    assertThat(getAllDocumentsOfIndex(
      new CamundaActivityEventIndex(FIRST_DEFINITION_KEY).getIndexName(),
      CamundaActivityEventDto.class
    )).extracting(CamundaActivityEventDto::getActivityInstanceId)
      .containsExactlyInAnyOrder(
        FIRST_DEFINITION_KEY + PROCESS_START_SUFFIX,
        FIRST_DEFINITION_KEY + PROCESS_END_SUFFIX,
        OTHER_INSTANCE_ID,
        FIRST_INSTANCE_ID + TASK_START_SUFFIX,
        FIRST_INSTANCE_ID + TASK_END_SUFFIX
      );
    assertThat(getAllDocumentsOfIndex(
      new CamundaActivityEventIndex(SECOND_DEFINITION_KEY).getIndexName(),
      CamundaActivityEventDto.class
    )).extracting(CamundaActivityEventDto::getActivityInstanceId)
      .containsExactlyInAnyOrder(
        SECOND_DEFINITION_KEY + PROCESS_START_SUFFIX,
        SECOND_DEFINITION_KEY + PROCESS_END_SUFFIX,
        OTHER_INSTANCE_ID,
        SECOND_INSTANCE_ID + TASK_START_SUFFIX,
        SECOND_INSTANCE_ID + TASK_END_SUFFIX
      );

    // when
    upgradePlan.execute();

    // then
    assertThat(getAllDocumentsOfIndex(
      new CamundaActivityEventIndex(FIRST_DEFINITION_KEY).getIndexName(),
      CamundaActivityEventDto.class
    )).hasSize(5)
      .extracting(CamundaActivityEventDto::getActivityInstanceId)
      .contains(FIRST_INSTANCE_ID + PROCESS_START_SUFFIX,
                FIRST_INSTANCE_ID + PROCESS_END_SUFFIX,
                OTHER_INSTANCE_ID,
                // These are the IDs of the original documents
                "c5c0bad8-da85-484b-a3a9-b8ec1ecf5e49" + TASK_START_SUFFIX,
                "d5c0bad8-da85-484b-a3a9-b8ec1ecf5e49" + TASK_END_SUFFIX
      );
    assertThat(getAllDocumentsOfIndex(
      new CamundaActivityEventIndex(SECOND_DEFINITION_KEY).getIndexName(),
      CamundaActivityEventDto.class
    )).hasSize(5)
      .extracting(CamundaActivityEventDto::getActivityInstanceId)
      .contains(SECOND_INSTANCE_ID + PROCESS_START_SUFFIX,
                SECOND_INSTANCE_ID + PROCESS_END_SUFFIX,
                OTHER_INSTANCE_ID,
                // These are the IDs of the original documents
                "x5c0bad8-da85-484b-a3a9-b8ec1ecf5e49" + TASK_START_SUFFIX,
                "y5c0bad8-da85-484b-a3a9-b8ec1ecf5e49" + TASK_END_SUFFIX
      );
  }

  @SneakyThrows
  private void createCamundaActivityEventV1IndexForDefinitionKey(final String definitionKey) {
    // We create the original camunda event indices in the same way as the schema manager
    IndexMappingCreator mapping = new CamundaActivityEventIndexV1(definitionKey);
    final String templateName = indexNameService.getOptimizeIndexNameForAliasAndVersion(mapping);
    final String pattern = String.format("%s-%s", templateName, "*");
    final Settings indexSettings = IndexSettingsBuilder.buildAllSettings(
      upgradeDependencies.getConfigurationService(),
      mapping
    );
    PutIndexTemplateRequest templateRequest = new PutIndexTemplateRequest(templateName)
      .version(mapping.getVersion())
      .mapping(mapping.getSource())
      .settings(indexSettings)
      .patterns(Collections.singletonList(pattern));
    upgradeDependencies.getEsClient()
      .getHighLevelClient()
      .indices()
      .putTemplate(templateRequest, RequestOptions.DEFAULT);
    final String aliasName = indexNameService.getOptimizeIndexAliasForIndex(mapping.getIndexName());
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(mapping);
    Alias writeAlias = new Alias(aliasName).writeIndex(true);
    CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName).alias(writeAlias);
    upgradeDependencies.getEsClient().getHighLevelClient().indices().create(createIndexRequest, RequestOptions.DEFAULT);
  }

}
