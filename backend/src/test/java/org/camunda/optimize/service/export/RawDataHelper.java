/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RawDataHelper {
  private static final String FIXED_TIME = "2018-02-23T14:31:08.048+01:00";
  private static final String FIXED_TIME_VARIABLE = "2018-02-23T12:31:08.048+01:00";
  // Process Columns: processDefinitionKey, processDefinitionId, processInstanceId, startDate, endDate, durationInMs,
  // engineName, tenantId, 3 variable fields
  public static final int NUMBER_OF_RAW_PROCESS_REPORT_COLUMNS = 12;
  // Decision Columns: decisionDefinitionKey, decisionDefinitionId, decisionInstanceId, evaluationDateTime,
  // engineName, tenantId, 7 input fields, 14 output fields
  public static final int NUMBER_OF_RAW_DECISION_REPORT_COLUMNS = 28;


  public static List<RawDataProcessInstanceDto> getRawDataProcessInstanceDtos() {
    final List<RawDataProcessInstanceDto> toMap = new ArrayList<>();

    final RawDataProcessInstanceDto instance1 = new RawDataProcessInstanceDto();
    instance1.setProcessDefinitionId("test_id");
    instance1.setProcessDefinitionKey("test_key");
    instance1.setStartDate(OffsetDateTime.parse(FIXED_TIME));
    instance1.setEndDate(OffsetDateTime.parse(FIXED_TIME));
    instance1.setDuration(0L);
    instance1.setBusinessKey("aBusinessKey");
    instance1.setEngineName("engine");
    instance1.setTenantId("tenant");

    final Map<String, Object> variables1 = new HashMap<>();
    variables1.put("1", "test");
    variables1.put("3", "test");
    instance1.setVariables(variables1);
    toMap.add(instance1);

    final RawDataProcessInstanceDto instance2 = new RawDataProcessInstanceDto();
    final Map<String, Object> variables2 = new HashMap<>();
    variables2.put("2", OffsetDateTime.parse(FIXED_TIME_VARIABLE));
    instance2.setVariables(variables2);
    toMap.add(instance2);

    final RawDataProcessInstanceDto instance3 = new RawDataProcessInstanceDto();
    toMap.add(instance3);

    return toMap;
  }

  public static List<RawDataProcessInstanceDto> getRawDataProcessInstanceDtoWithVariables(
    final Map<String, Object> variables) {
    final List<RawDataProcessInstanceDto> toMap = new ArrayList<>();

    final RawDataProcessInstanceDto instance = new RawDataProcessInstanceDto();
    instance.setProcessDefinitionId("test_id");
    instance.setProcessDefinitionKey("test_key");
    instance.setStartDate(OffsetDateTime.parse(FIXED_TIME));
    instance.setEndDate(OffsetDateTime.parse(FIXED_TIME));
    instance.setDuration(0L);
    instance.setBusinessKey("aBusinessKey");
    instance.setEngineName("engine");
    instance.setTenantId("tenant");

    instance.setVariables(variables);
    toMap.add(instance);

    return toMap;
  }

  public static List<RawDataDecisionInstanceDto> getRawDataDecisionInstanceDtos() {
    final List<RawDataDecisionInstanceDto> toMap = new ArrayList<>();

    final RawDataDecisionInstanceDto instance1 = new RawDataDecisionInstanceDto();
    toMap.add(instance1);
    instance1.setDecisionDefinitionId("test_id");
    instance1.setDecisionDefinitionKey("test_key");
    instance1.setEvaluationDateTime(OffsetDateTime.parse(FIXED_TIME));
    instance1.setEngineName("engine");
    instance1.setTenantId("tenant");

    final Map<String, InputVariableEntry> inputs = new HashMap<>();
    inputs.put("1", new InputVariableEntry("1", "1", VariableType.BOOLEAN, "true"));
    inputs.put("2", new InputVariableEntry("2", "2", VariableType.DATE, FIXED_TIME_VARIABLE));
    inputs.put("3", new InputVariableEntry("3", "3", VariableType.DOUBLE, "3.3"));
    inputs.put("4", new InputVariableEntry("4", "4", VariableType.INTEGER, "1"));
    inputs.put("5", new InputVariableEntry("5", "5", VariableType.LONG, "1000"));
    inputs.put("6", new InputVariableEntry("6", "6", VariableType.SHORT, "1"));
    inputs.put("7", new InputVariableEntry("7", "7", VariableType.STRING, "hello"));
    instance1.setInputVariables(inputs);

    final Map<String, OutputVariableEntry> outputs = new HashMap<>();
    outputs.put("1", new OutputVariableEntry("1", "1", VariableType.BOOLEAN, "true"));
    outputs.put("2", new OutputVariableEntry("2", "2", VariableType.BOOLEAN, "true", "false"));
    outputs.put("3", new OutputVariableEntry("3", "3", VariableType.DATE, FIXED_TIME_VARIABLE));
    outputs.put("4", new OutputVariableEntry("4", "4", VariableType.DATE, FIXED_TIME_VARIABLE, FIXED_TIME_VARIABLE));
    outputs.put("5", new OutputVariableEntry("5", "5", VariableType.DOUBLE, "3.3"));
    outputs.put("6", new OutputVariableEntry("6", "6", VariableType.DOUBLE, "3.3", "4.4"));
    outputs.put("7", new OutputVariableEntry("7", "7", VariableType.INTEGER, "1"));
    outputs.put("8", new OutputVariableEntry("8", "8", VariableType.INTEGER, "1", "2"));
    outputs.put("9", new OutputVariableEntry("9", "9", VariableType.LONG, "1000"));
    outputs.put("10", new OutputVariableEntry("10", "10", VariableType.LONG, "1000", "2000"));
    outputs.put("11", new OutputVariableEntry("11", "11", VariableType.SHORT, "1"));
    outputs.put("12", new OutputVariableEntry("12", "12", VariableType.SHORT, "1", "2"));
    outputs.put("13", new OutputVariableEntry("13", "13", VariableType.STRING, "hello"));
    outputs.put("14", new OutputVariableEntry("14", "14", VariableType.STRING, "hello1", "hello2"));
    instance1.setOutputVariables(outputs);

    final RawDataDecisionInstanceDto instance2 = new RawDataDecisionInstanceDto();
    instance2.setInputVariables(inputs);
    instance2.setOutputVariables(outputs);
    toMap.add(instance2);

    final RawDataDecisionInstanceDto instance3 = new RawDataDecisionInstanceDto();
    toMap.add(instance3);

    return toMap;
  }
}
