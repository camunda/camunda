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
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.j5templates.OperateZeebeSearchAbstractIT;
import io.camunda.operate.util.j5templates.SearchFieldValueMap;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.zeebe.operation.process.modify.ModifyProcessInstanceHandler;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

public class ProcessInstanceRestServiceIT extends OperateZeebeSearchAbstractIT {
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  @Autowired private VariableTemplate variableTemplate;
  @Autowired private ModifyProcessInstanceHandler modifyProcessInstanceHandler;

  @Override
  protected void runAdditionalBeforeAllSetup() {
    modifyProcessInstanceHandler.setZeebeClient(zeebeClient);
    final Long processDefinitionKey = operateTester.deployProcess("demoProcess_v_2.bpmn");
    operateTester.waitForProcessDeployed(processDefinitionKey);
  }

  @Test
  public void testGetInstanceByIdWithInvalidId() throws Exception {
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/4503599627535750:";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetIncidentsByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/incidents";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetVariablesByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/variables";
    final MvcResult mvcResult =
        mockMvcManager.postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeStatesByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-states";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeMetadataByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-metadata";
    final MvcResult mvcResult =
        mockMvcManager.postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void shouldAddTokenWithVariables() throws Exception {
    final Long processInstanceKey = operateTester.startProcess("demoProcess", "{\"a\": \"b\"}");
    operateTester.waitForProcessInstanceStarted(processInstanceKey);

    var result =
        testSearchRepository.searchTerms(
            flowNodeInstanceTemplate.getFullQualifiedName(),
            new SearchFieldValueMap()
                .addFieldValue(FlowNodeInstanceTemplate.FLOW_NODE_ID, "taskB")
                .addFieldValue(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey),
            FlowNodeInstanceEntity.class,
            1);
    assertThat(result).isEmpty();

    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId("taskB")
                .setVariables(Map.of("taskB", List.of(Map.of("c", "d")))));

    operateTester.modifyProcessInstanceOperation(processInstanceKey, modifications);
    operateTester.waitForOperationFinished(processInstanceKey);
    operateTester.waitForFlowNode(processInstanceKey, "taskB");

    result =
        testSearchRepository.searchTerms(
            flowNodeInstanceTemplate.getFullQualifiedName(),
            new SearchFieldValueMap()
                .addFieldValue(FlowNodeInstanceTemplate.FLOW_NODE_ID, "taskB")
                .addFieldValue(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey),
            FlowNodeInstanceEntity.class,
            1);
    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getState()).isEqualTo(FlowNodeState.ACTIVE);

    final var variableResult =
        testSearchRepository.searchTerms(
            variableTemplate.getFullQualifiedName(),
            new SearchFieldValueMap()
                .addFieldValue(VariableTemplate.NAME, "c")
                .addFieldValue(VariableTemplate.SCOPE_KEY, result.get(0).getKey()),
            VariableEntity.class,
            10);
    assertThat(variableResult).isNotEmpty();
    assertThat(variableResult.get(0).getValue()).isEqualTo("\"d\"");
  }
}
