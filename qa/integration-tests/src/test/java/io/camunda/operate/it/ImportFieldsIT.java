/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static io.camunda.operate.util.ElasticsearchUtil.requestOptionsFor;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Map;

import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.PayloadUtil;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ImportFieldsIT extends OperateZeebeAbstractIT {

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

  @Test public void testThrottleBatchSize() throws Exception {

    // having
    ElasticsearchUtil.setRequestOptions(requestOptionsFor(1024 * 32 * 10));

    var bigVarBuilder = new StringBuilder();
    for (int i = 0; i < 1024 * 32; i++) {
      bigVarBuilder.append("a");
    }

    var vars = new StringBuilder("{");
    for (int i = 0; i < 50; i++) {
      vars.append("\"test" + i + "\" : \"" + bigVarBuilder.toString() + "\", ");
    }
    vars.append("\"end\" : \"" + bigVarBuilder.toString() + "\"}");

    // when
    tester.deployProcess("single-task.bpmn").waitUntil().processIsDeployed();

    var processInstanceKeys = new ArrayList<Long>();
    for (int i = 0; i < 5; i++) {
      processInstanceKeys.add(
          tester.startProcessInstance("process", vars.toString()).waitUntil().processInstanceIsStarted()
              .getProcessInstanceKey());
    }

    tester.waitUntil().variableExists("end");
    ElasticsearchUtil.setRequestOptions(RequestOptions.DEFAULT);
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
