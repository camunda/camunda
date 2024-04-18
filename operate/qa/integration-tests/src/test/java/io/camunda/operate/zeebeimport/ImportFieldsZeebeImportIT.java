/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.util.ElasticsearchUtil.requestOptionsFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.PayloadUtil;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import java.util.ArrayList;
import java.util.Map;
import org.elasticsearch.client.RequestOptions;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ImportFieldsZeebeImportIT extends OperateZeebeAbstractIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportFieldsZeebeImportIT.class);

  @Autowired private PayloadUtil payloadUtil;

  @Autowired private UpdateVariableHandler updateVariableHandler;

  @Override
  @Before
  public void before() {
    super.before();
    updateVariableHandler.setZeebeClient(zeebeClient);
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
    ElasticsearchUtil.setRequestOptions(requestOptionsFor(1024 * 32 * 10));

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
    ElasticsearchUtil.setRequestOptions(RequestOptions.DEFAULT);
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
