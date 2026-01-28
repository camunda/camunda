/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.PayloadUtil;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import java.util.ArrayList;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ImportFieldsZeebeIT extends OperateZeebeAbstractIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportFieldsZeebeIT.class);

  @Autowired private PayloadUtil payloadUtil;

  @Autowired private UpdateVariableHandler updateVariableHandler;

  @Override
  @Before
  public void before() {
    super.before();
    updateVariableHandler.setOperateAdapter(operateServicesAdapter);
  }

  @Test
  // OPE-900
  // See also:
  // https://discuss.elastic.co/t/error-document-contains-at-least-one-immense-term-in-field/66486
  public void testVariableValueSizeCanBeHigherThan32KB() throws Exception {
    // having
    //  big json string
    final String bigJSONVariablePayload =
        payloadUtil.readStringFromClasspath("/large-payload.json");
    //  and object with two vars
    final Map<String, Object> variables = payloadUtil.parsePayload(bigJSONVariablePayload);

    // when
    tester
        .deployProcess("single-task.bpmn")
        .and()
        .startProcessInstance("process", bigJSONVariablePayload)
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .waitUntil()
        .variableExists("small")
        .and()
        .variableExists("large");

    // then
    assertThat(tester.hasVariable("small", "\"" + variables.get("small").toString() + "\""))
        .isTrue();
    assertThat(tester.hasVariable("large", "\"" + variables.get("large").toString() + "\""))
        .isTrue();
  }

  @Test
  // OPE-900
  // See also:
  // https://discuss.elastic.co/t/error-document-contains-at-least-one-immense-term-in-field/66486
  public void testUpdateVariableValueSizeCanBeHigherThan32KB() throws Exception {
    // having
    //  big json string
    final String bigJSONVariablePayload = "\"" + buildStringWithLengthOf(32 * 1024 + 42) + "\"";
    final String varName = "name";

    // when
    tester
        .deployProcess("single-task.bpmn")
        .and()
        .startProcessInstance("process", "{\"" + varName + "\": \"smallValue\"}")
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .waitUntil()
        .variableExists(varName)
        .updateVariableOperation(varName, bigJSONVariablePayload)
        .waitUntil()
        .operationIsCompleted();

    // then
    assertThat(tester.hasVariable(varName, bigJSONVariablePayload)).isTrue();
  }

  @Test
  public void testThrottleBatchSize() throws Exception {

    // having

    final var bigVarBuilder = new StringBuilder();
    for (int i = 0; i < 1024 * 32; i++) {
      bigVarBuilder.append("a");
    }

    final var vars = new StringBuilder("{");
    for (int i = 0; i < 50; i++) {
      vars.append("\"test" + i + "\" : \"" + bigVarBuilder.toString() + "\", ");
    }
    vars.append("\"end\" : \"" + bigVarBuilder.toString() + "\"}");

    // when
    tester.deployProcess("single-task.bpmn").waitUntil().processIsDeployed();

    final var processInstanceKeys = new ArrayList<Long>();
    for (int i = 0; i < 5; i++) {
      processInstanceKeys.add(
          tester
              .startProcessInstance("process", vars.toString())
              .waitUntil()
              .processInstanceIsStarted()
              .getProcessInstanceKey());
    }

    tester.waitUntil().variableExists("end");
  }

  protected String buildStringWithLengthOf(final int length) {
    final StringBuilder result = new StringBuilder();
    final String fillChar = "a";
    for (int i = 0; i < length; i++) {
      result.append(fillChar);
    }
    return result.toString();
  }
}
