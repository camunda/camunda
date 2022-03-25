/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.archive;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.archive.ProcessInstanceArchivingService;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceArchiveIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessInstanceArchivingServiceIT extends AbstractIT {

  @Test
  public void processInstanceArchiverIsNotStartedByDefault() {
    assertThat(getProcessInstanceArchivingService().isScheduledToRun()).isFalse();
  }

  @Test
  public void processInstanceArchiverCanBeDisabled() {
    getProcessInstanceArchivingService().stopArchiving();
    embeddedOptimizeExtension.getConfigurationService().getDataArchiveConfiguration().setEnabled(false);
    embeddedOptimizeExtension.reloadConfiguration();
    assertThat(getProcessInstanceArchivingService().isScheduledToRun()).isFalse();
  }

  @Test
  public void processInstanceArchiverStoppedSuccessfully() {
    getProcessInstanceArchivingService().stopArchiving();
    try {
      assertThat(getProcessInstanceArchivingService().isScheduledToRun()).isFalse();
    } finally {
      getProcessInstanceArchivingService().startArchiving();
    }
  }

  @Test
  public void processInstanceArchiverCreatesMissingArchiveIndices() {
    // given
    assertThat(getAllProcessInstanceArchiveIndexNames()).isEmpty();

    final String firstProcessKey = "firstProcess";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(firstProcessKey));
    importAllEngineEntitiesFromScratch();

    // when
    getProcessInstanceArchivingService().archiveCompletedProcessInstances();

    // then
    assertThat(getAllProcessInstanceArchiveIndexNames()).hasSize(1)
      .containsExactly(getExpectedArchiveIndexName(firstProcessKey));

    // when
    final String secondProcessKey = "secondProcess";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(secondProcessKey));
    importAllEngineEntitiesFromLastIndex();
    getProcessInstanceArchivingService().archiveCompletedProcessInstances();

    // then
    assertThat(getAllProcessInstanceArchiveIndexNames()).hasSize(2)
      .containsExactlyInAnyOrder(
        getExpectedArchiveIndexName(firstProcessKey),
        getExpectedArchiveIndexName(secondProcessKey)
      );
  }

  private String getExpectedArchiveIndexName(final String firstProcessKey) {
    return embeddedOptimizeExtension.getIndexNameService()
      .getOptimizeIndexNameWithVersion(new ProcessInstanceArchiveIndex(firstProcessKey));
  }

  private ProcessInstanceArchivingService getProcessInstanceArchivingService() {
    return embeddedOptimizeExtension.getProcessInstanceArchivingService();
  }

  @SneakyThrows
  private List<String> getAllProcessInstanceArchiveIndexNames() {
    return Arrays.stream(
        elasticSearchIntegrationTestExtension.getOptimizeElasticClient().getHighLevelClient()
          .indices().get(
            new GetIndexRequest("*"),
            elasticSearchIntegrationTestExtension.getOptimizeElasticClient().requestOptions()
          ).getIndices())
      .filter(index -> index.contains(ElasticsearchConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX))
      .collect(Collectors.toList());
  }

}
