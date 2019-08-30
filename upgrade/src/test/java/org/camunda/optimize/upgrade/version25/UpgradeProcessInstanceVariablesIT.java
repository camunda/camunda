/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version25;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.version25.indexes.Version25CollectionIndex;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom25To26;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.version25.indexes.Version25ProcessInstanceIndex;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class UpgradeProcessInstanceVariablesIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.5.0";

  private static final DecisionDefinitionIndex DECISION_DEFINITION_INDEX_OBJECT = new DecisionDefinitionIndex();
  private static final SingleDecisionReportIndex SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex();
  private static final Version25ProcessInstanceIndex PROCESS_INSTANCE_INDEX = new Version25ProcessInstanceIndex();
  private static final Version25CollectionIndex COLLECTION_INDEX = new Version25CollectionIndex();
  private static final DashboardIndex DASHBOARD_INDEX = new DashboardIndex();
  private static final CombinedReportIndex COMBINED_REPORT_INDEX = new CombinedReportIndex();

  private static final String PROCESS_INSTANCE_WITH_DOUBLE_VAR = "973963f1-bcf9-11e9-82a8-0242ac120002";
  private static final String PROCESS_INSTANCE_WITH_BOOL_VAR = "74332c45-bcf9-11e9-82a8-0242ac120002";
  private static final String PROCESS_INSTANCE_WITH_LONG_VAR = "8f65741a-bcf9-11e9-82a8-0242ac120002";
  private static final String PROCESS_INSTANCE_WITH_DATE_VAR = "ca30b930-bcf9-11e9-82a8-0242ac120002";
  private static final String PROCESS_INSTANCE_WITH_STRING_VAR = "a3332fb8-bcf9-11e9-82a8-0242ac120002";
  private static final String PROCESS_INSTANCE_WITH_SHORT_VAR = "7b94ab3c-bcf9-11e9-82a8-0242ac120002";
  private static final String PROCESS_INSTANCE_WITH_INT_VAR = "89b26873-bcf9-11e9-82a8-0242ac120002";
  private static final String PROCESS_INSTANCE_WITH_MULTIPLE_STRING_VARS = "f1f53c27-bcf9-11e9-82a8-0242ac120002";
  private static final String PROCESS_INSTANCE_WITH_MULTIPLE_INT_VARS_AND_ONE_STRING_VAR =
    "1ec73bf2-bcfa-11e9-82a8-0242ac12000";

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      DECISION_DEFINITION_INDEX_OBJECT,
      SINGLE_DECISION_REPORT_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      PROCESS_INSTANCE_INDEX,
      COLLECTION_INDEX,
      COMBINED_REPORT_INDEX,
      DASHBOARD_INDEX
    ));

    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/process_instance/25-process-instance-bulk");
  }

  @Test
  public void doubleVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance = getProcessInstanceById(PROCESS_INSTANCE_WITH_DOUBLE_VAR);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(1));
    assertThat(variables.get(0).getName(), is("foo"));
    assertThat(variables.get(0).getType(), is(VariableType.DOUBLE.getId()));
    assertThat(variables.get(0).getValue(), is("1.0"));
  }

  @Test
  public void dateVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance = getProcessInstanceById(PROCESS_INSTANCE_WITH_DATE_VAR);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(1));
    assertThat(variables.get(0).getName(), is("foo"));
    assertThat(variables.get(0).getType(), is(VariableType.DATE.getId()));
    assertThat(variables.get(0).getValue(), is("2019-08-12T11:20:43.000+0200"));
  }

  @Test
  public void stringVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance = getProcessInstanceById(PROCESS_INSTANCE_WITH_STRING_VAR);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(1));
    assertThat(variables.get(0).getName(), is("foo"));
    assertThat(variables.get(0).getType(), is(VariableType.STRING.getId()));
    assertThat(variables.get(0).getValue(), is("aString"));
  }

  @Test
  public void shortVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance = getProcessInstanceById(PROCESS_INSTANCE_WITH_SHORT_VAR);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(1));
    assertThat(variables.get(0).getName(), is("Foo"));
    assertThat(variables.get(0).getType(), is(VariableType.SHORT.getId()));
    assertThat(variables.get(0).getValue(), is("1"));
  }

  @Test
  public void integerVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance = getProcessInstanceById(PROCESS_INSTANCE_WITH_INT_VAR);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(1));
    assertThat(variables.get(0).getName(), is("foo"));
    assertThat(variables.get(0).getType(), is(VariableType.INTEGER.getId()));
    assertThat(variables.get(0).getValue(), is("1"));
  }

  @Test
  public void longVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance = getProcessInstanceById(PROCESS_INSTANCE_WITH_LONG_VAR);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(1));
    assertThat(variables.get(0).getName(), is("foo"));
    assertThat(variables.get(0).getType(), is(VariableType.LONG.getId()));
    assertThat(variables.get(0).getValue(), is("1"));
  }

  @Test
  public void booleanVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance = getProcessInstanceById(PROCESS_INSTANCE_WITH_BOOL_VAR);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(1));
    assertThat(variables.get(0).getName(), is("foo"));
    assertThat(variables.get(0).getType(), is(VariableType.BOOLEAN.getId()));
    assertThat(variables.get(0).getValue(), is("true"));
  }

  @Test
  public void multipleStringVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance = getProcessInstanceById(PROCESS_INSTANCE_WITH_MULTIPLE_STRING_VARS);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(2));
    variables.forEach(
      var -> {
        assertThat(var.getType(), is(VariableType.STRING.getId()));
        assertThat(var.getValue(), notNullValue());
      }
    );
  }

  @Test
  public void multipleStringAndIntVariablesCanBeUpgraded() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom25To26().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    ProcessInstanceDto processInstance =
      getProcessInstanceById(PROCESS_INSTANCE_WITH_MULTIPLE_INT_VARS_AND_ONE_STRING_VAR);
    List<SimpleProcessVariableDto> variables = processInstance.getVariables();
    assertThat(variables.size(), is(3));
    variables.forEach(
      var -> assertThat(var.getValue(), notNullValue())
    );
  }

  @SneakyThrows
  private ProcessInstanceDto getProcessInstanceById(final String id) {
    final GetResponse reportResponse = getProcessInstance(id);
    return objectMapper.readValue(
      reportResponse.getSourceAsString(), ProcessInstanceDto.class
    );
  }

  @SneakyThrows
  private GetResponse getProcessInstance(final String id) {
    return prefixAwareClient.get(
      new GetRequest(ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME, ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME, id),
      RequestOptions.DEFAULT
    );
  }

}