/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.PayloadUtil;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ImportFieldsIT extends OperateZeebeIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(ImportFieldsIT.class);

  @Autowired
  private PayloadUtil payloadUtil;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @Before
  public void before() {
    super.before();
    updateVariableHandler.setZeebeClient(zeebeClient);
  }

  @Test
  // OPE-900
  // See also: https://discuss.elastic.co/t/error-document-contains-at-least-one-immense-term-in-field/66486
  public void testVariableValueSizeCanBeHigherThan32KB() throws Exception {
    // having
    //  big json string
    String bigJSONVariablePayload = payloadUtil.readStringFromClasspath("/large-payload.json");
    //  and object with two vars
    Map<String, Object> variables = payloadUtil.parsePayload(bigJSONVariablePayload);

    // when
    tester
      .deployProcess("single-task.bpmn")
      .and()
      .startProcessInstance("process", bigJSONVariablePayload)
      .waitUntil().processInstanceIsStarted()
      .and()
      .waitUntil().variableExists("small")
      .and().variableExists("large");

    // then
    assertThat(tester.hasVariable("small","\""+variables.get("small").toString()+"\"")).isTrue();
    assertThat(tester.hasVariable("large","\""+variables.get("large").toString()+"\"")).isTrue();
  }

  @Test
  // OPE-900
  // See also: https://discuss.elastic.co/t/error-document-contains-at-least-one-immense-term-in-field/66486
  public void testUpdateVariableValueSizeCanBeHigherThan32KB() throws Exception {
    // having
    //  big json string
    String bigJSONVariablePayload = "\"" + buildStringWithLengthOf(32 * 1024 + 42) + "\"";
    String varName = "name";

    // when
    tester
      .deployProcess("single-task.bpmn")
      .and()
      .startProcessInstance("process", "{\"" + varName + "\": \"smallValue\"}")
      .waitUntil().processInstanceIsStarted()
      .and()
      .waitUntil().variableExists(varName)
      .updateVariableOperation(varName, bigJSONVariablePayload)
      .waitUntil().operationIsCompleted();

    // then
    assertThat(tester.hasVariable(varName, bigJSONVariablePayload)).isTrue();
  }

  protected String buildStringWithLengthOf(int length) {
    StringBuilder result = new StringBuilder();
    String fillChar = "a";
    for (int i = 0; i < length; i++) {
      result.append(fillChar);
    }
    return result.toString();
  }

}
