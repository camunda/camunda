/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate33To34;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade33To34PlanFactory;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class MigrateUserTaskDurationsIT extends AbstractUpgrade33IT {

  private static final String DEFINITION_KEY = "userTaskProcess";

  @SneakyThrows
  @BeforeEach
  public void setUp() {
    super.setUp();
    executeBulk("steps/3.3/process/33-process-instances-with-usertask-durations.json");
  }

  @SneakyThrows
  @Test
  public void userTaskWorkAndIdleDurationsAreRecalculated() {
    // given
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan(prefixAwareClient);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertDurations(getUserTasksOfInstanceWithId("single-claim"), 1400L, 500L, 900L);
    assertDurations(getUserTasksOfInstanceWithId("multi-claim"), 1400L, 800L, 600L);
    assertDurations(getUserTasksOfInstanceWithId("running-unclaimed"), null, null, null);
    assertDurations(getUserTasksOfInstanceWithId("running-claimed"), null, 500L, null);
    assertDurations(getUserTasksOfInstanceWithId("completed-no-claims"), 1400L, 0L, 1400L);
    assertDurations(getUserTasksOfInstanceWithId("cancelled-no-claims"), 1400L, 1400L, 0L);
  }

  @SneakyThrows
  @Test
  public void userTaskClaimDateFieldIsRemoved() {
    // given
    final UpgradePlan upgradePlan = new Upgrade33To34PlanFactory().createUpgradePlan(prefixAwareClient);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getAllUserTasksInInstanceIndex())
      .isNotEmpty()
      .allSatisfy(userTask -> assertThat(userTask).doesNotContainKey("claimDate"));
  }

  private void assertDurations(final List<UserTaskInstanceDto> userTasks, final Long totalDuration,
                               final Long idleDuration, final Long workDuration) {
    assertThat(userTasks).isNotEmpty();
    assertThat(userTasks).extracting(UserTaskInstanceDto::getTotalDurationInMs).containsOnly(totalDuration);
    assertThat(userTasks).extracting(UserTaskInstanceDto::getWorkDurationInMs).containsOnly(workDuration);
    assertThat(userTasks).extracting(UserTaskInstanceDto::getIdleDurationInMs).containsOnly(idleDuration);
  }

  private List<UserTaskInstanceDto> getUserTasksOfInstanceWithId(final String instanceId) {
    return getDocumentOfIndexByIdAs(
      indexNameService.getOptimizeIndexAliasForIndex(new ProcessInstanceIndex(DEFINITION_KEY)),
      instanceId,
      ProcessInstanceDto.class
    ).map(ProcessInstanceDto::getUserTasks).orElse(Collections.emptyList());
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  private List<Map<String, Object>> getAllUserTasksInInstanceIndex() {
    final SearchHit[] allInstanceDocs = getAllDocumentsOfIndex(
      indexNameService.getOptimizeIndexAliasForIndex(new ProcessInstanceIndex(DEFINITION_KEY))
    );
    return Arrays.stream(allInstanceDocs)
      .flatMap(
        inst -> ((List<Map<String, Object>>) inst.getSourceAsMap().get(ProcessInstanceDto.Fields.userTasks)).stream()
      ).collect(toList());
  }

}
