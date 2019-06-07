/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessDefinitionType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.report.SingleDecisionReportType;
import org.camunda.optimize.service.es.schema.type.report.SingleProcessReportType;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom24To25;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class UpgradeProcessInstanceDataIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.4.0";
  private static final String TO_VERSION = "2.5.0";


  private static final ProcessInstanceType PROCESS_INSTANCE_TYPE = new ProcessInstanceType();
  private static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private static final String PROC_INST_MULTIPLECLAIMS_VERSION_24_ID = "5520870c-7e02-11e9-ab0e-0242ac120002";
  private static final String PROC_INST_NO_USERTASKS_VERSION_24_ID = "355db12c-8120-11e9-ab39-0242ac120003";

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_TYPE,
      PROCESS_INSTANCE_TYPE,
      new SingleDecisionReportType(),
      new ProcessDefinitionType(),
      new SingleProcessReportType(),
      new DecisionDefinitionType()
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/process_instance/24-process-instance-bulk");
  }

  @Test
  public void userTasksHaveNewClaimDateField() throws Exception {
    //given
    UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ProcessInstanceDto processInstanceDto = getProcessInstanceDataById("2df54be3-7ca1-11e9-a827-0242ac120002");

    processInstanceDto.getUserTasks().forEach(userTask -> {
      if ("approveInvoice".equals(userTask.getActivityId())) {
        assertThat(dateFormatter.format(userTask.getClaimDate()), is("2019-05-22T16:52:11.413+0200"));
      } else {
        assertThat(userTask.getClaimDate(), is(nullValue()));
      }
    });
  }

  @Test
  public void userTaskHasCorrectClaimDateAddedOnMultipleClaims() throws Exception {
    //given
    UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final ProcessInstanceDto processInstanceDto = getProcessInstanceDataById(PROC_INST_MULTIPLECLAIMS_VERSION_24_ID);

    final Optional<UserTaskInstanceDto> userTask = processInstanceDto.getUserTasks()
      .stream()
      .filter(task -> "userTask1".equals(task.getActivityId()))
      .findFirst();

    assertThat(userTask.isPresent(), is(true));
    assertThat(dateFormatter.format(userTask.get().getClaimDate()), is("2019-05-20T11:00:09.574+0200"));
  }

  @Test
  public void onlyProcessInstancesWithUserTasksAreUpgraded() throws Exception {
    //given
    UpgradePlan upgradePlan = new UpgradeFrom24To25().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final GetResponse processInstWithUserTasks = getProcessInstanceByIdResponse(PROC_INST_MULTIPLECLAIMS_VERSION_24_ID);
    assertThat(processInstWithUserTasks.getVersion(), is(2L));

    final GetResponse processInstNoUserTasks = getProcessInstanceByIdResponse(PROC_INST_NO_USERTASKS_VERSION_24_ID);
    assertThat(processInstNoUserTasks.getVersion(), is(1L));
  }

  private String getProcessInstanceIndexAlias() {
    return getOptimizeIndexAliasForType(PROCESS_INSTANCE_TYPE.getType());
  }

  private ProcessInstanceDto getProcessInstanceDataById(String id) throws IOException {
    final GetResponse reportResponse = getProcessInstanceByIdResponse(id);
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), ProcessInstanceDto.class
    );
  }

  private GetResponse getProcessInstanceByIdResponse(final String id) throws IOException {
    return restClient.get(
      new GetRequest(getProcessInstanceIndexAlias(), PROCESS_INSTANCE_TYPE.getType(), id), RequestOptions.DEFAULT
    );
  }
}
