/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.metadata.AliasMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

public class ProcessInstanceMigrationIT extends AbstractUpgrade30IT {


  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.0/process_instance/30-process-instance-bulk");
    executeBulk("steps/3.0/process_instance/30-process-instance-event-bulk");
    executeBulk("steps/3.0/process_instance/30-process-publish-state-bulk");
  }

  @Test
  public void activitiesInProcessInstanceContainProcessInstanceId() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getProcessInstances()).hasSize(1);
    ProcessInstanceDto processInstance = getProcessInstances().get(0);
    assertThat(processInstance.getEvents())
      .hasSize(3)
      .allSatisfy(
        event ->
          assertThat(event.getProcessInstanceId()).isEqualTo(processInstance.getProcessInstanceId())
      );
  }

  @SneakyThrows
  @Test
  public void activitiesInEventProcessInstanceContainProcessInstanceId() {
    // given
    assertThatEventProcessInstanceIndexAliasesAreSet();
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getEventProcessInstances()).hasSize(1);
    ProcessInstanceDto processInstance = getEventProcessInstances().get(0);
    assertThat(processInstance.getEvents())
      .hasSize(3)
      .allSatisfy(
        event ->
          assertThat(event.getProcessInstanceId()).isEqualTo(processInstance.getProcessInstanceId())
      );
    assertThatEventProcessInstanceIndexAliasesAreSet();
  }

  private void assertThatEventProcessInstanceIndexAliasesAreSet() throws IOException {
    assertThat(getAliases(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*"))
      .hasSize(2)
      .allSatisfy(aliasMetaData -> {
        if (aliasMetaData.alias().contains(EVENT_PROCESS_INSTANCE_INDEX_PREFIX)) {
          assertThat(aliasMetaData.writeIndex()).isTrue();
        } else if (aliasMetaData.alias().contains(PROCESS_INSTANCE_INDEX_NAME)) {
          assertThat(aliasMetaData.writeIndex()).isFalse();
        } else {
          fail("Unexpected alias: " + aliasMetaData.alias());
        }
      });
  }

  private List<AliasMetaData> getAliases(final String indexName) throws IOException {
    final GetAliasesResponse aliases = prefixAwareClient.getAlias(
      new GetAliasesRequest().indices(indexName), RequestOptions.DEFAULT
    );
    return aliases.getAliases()
      .values()
      .stream()
      .flatMap(Collection::stream)
      .collect(toList());
  }

  private List<ProcessInstanceDto> getProcessInstances() {
    // apply suffix to only catch the versioned index names
    return getAllDocumentsOfIndexAs(PROCESS_INSTANCE_INDEX_NAME + "_*", ProcessInstanceDto.class);
  }

  private List<ProcessInstanceDto> getEventProcessInstances() {
    return getAllDocumentsOfIndexAs(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + "*", ProcessInstanceDto.class);
  }

}
