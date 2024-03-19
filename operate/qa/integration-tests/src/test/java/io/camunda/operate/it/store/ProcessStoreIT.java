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
package io.camunda.operate.it.store;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.ListViewJoinRelation;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessStoreIT extends OperateSearchAbstractIT {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired private ProcessIndex processDefinitionIndex;

  @Autowired private ProcessStore processStore;

  private ProcessEntity firstProcessDefinition;
  private ProcessEntity secondProcessDefinition;
  private ProcessInstanceForListViewEntity firstProcessInstance;
  private ProcessInstanceForListViewEntity secondProcessInstance;
  private ProcessInstanceForListViewEntity thirdProcessInstance;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final String resourceXml =
        testResourceManager.readResourceFileContentsAsString("demoProcess_v_1.bpmn");
    firstProcessDefinition =
        new ProcessEntity()
            .setKey(2251799813685248L)
            .setId("2251799813685248")
            .setBpmnProcessId("demoProcess")
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("Demo process")
            .setBpmnXml(resourceXml);
    testSearchRepository.createOrUpdateDocumentFromObject(
        processDefinitionIndex.getFullQualifiedName(),
        firstProcessDefinition.getId(),
        firstProcessDefinition);

    secondProcessDefinition =
        new ProcessEntity()
            .setKey(2251799813685249L)
            .setId("2251799813685249")
            .setBpmnProcessId("demoProcess-1")
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("Demo process 1")
            .setBpmnXml(resourceXml);
    testSearchRepository.createOrUpdateDocumentFromObject(
        processDefinitionIndex.getFullQualifiedName(),
        secondProcessDefinition.getId(),
        secondProcessDefinition);

    firstProcessInstance =
        new ProcessInstanceForListViewEntity()
            .setId("4503599627370497")
            .setKey(4503599627370497L)
            .setProcessDefinitionKey(firstProcessDefinition.getKey())
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_4503599627370497")
            .setTenantId(DEFAULT_TENANT_ID)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        firstProcessInstance.getId(),
        firstProcessInstance);

    secondProcessInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setProcessDefinitionKey(secondProcessDefinition.getKey())
            .setProcessInstanceKey(2251799813685251L)
            .setParentProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process child 1")
            .setBpmnProcessId("demoProcess-1")
            .setState(ProcessInstanceState.COMPLETED)
            .setTreePath("PI_2251799813685251")
            .setTenantId(DEFAULT_TENANT_ID)
            .setIncident(false)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        secondProcessInstance.getId(),
        secondProcessInstance);

    thirdProcessInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685252")
            .setKey(2251799813685252L)
            .setProcessDefinitionKey(secondProcessDefinition.getKey())
            .setProcessInstanceKey(2251799813685252L)
            .setParentProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process child 2")
            .setBpmnProcessId("demoProcess-1")
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685252")
            .setTenantId(DEFAULT_TENANT_ID)
            .setIncident(true)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        thirdProcessInstance.getId(),
        thirdProcessInstance);

    searchContainerManager.refreshIndices("*");
  }

  @Test
  public void testGetDistinctCountFor() {
    final Optional<Long> optionalCount =
        processStore.getDistinctCountFor(ListViewTemplate.BPMN_PROCESS_ID);

    assertThat(optionalCount).isNotNull();
    assertThat(optionalCount.get()).isEqualTo(2L);
  }

  @Test
  public void testGetProcessByKey() {
    final ProcessEntity result = processStore.getProcessByKey(secondProcessDefinition.getKey());

    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(secondProcessDefinition.getBpmnProcessId());
    assertThat(result.getKey()).isEqualTo(secondProcessDefinition.getKey());
  }

  @Test
  public void testGetDiagramByKey() {
    final String xml = processStore.getDiagramByKey(secondProcessDefinition.getKey());
    assertThat(xml)
        .isEqualTo(testResourceManager.readResourceFileContentsAsString("demoProcess_v_1.bpmn"));
  }

  @Test
  public void testGetProcessesGrouped() {
    final Map<ProcessStore.ProcessKey, List<ProcessEntity>> results =
        processStore.getProcessesGrouped(
            DEFAULT_TENANT_ID,
            Set.of(
                firstProcessDefinition.getBpmnProcessId(),
                secondProcessDefinition.getBpmnProcessId()));

    assertThat(results.values().size()).isEqualTo(2);
    assertThat(
            results
                .get(
                    new ProcessStore.ProcessKey(
                        firstProcessDefinition.getBpmnProcessId(), DEFAULT_TENANT_ID))
                .size())
        .isEqualTo(1);
    assertThat(
            results
                .get(
                    new ProcessStore.ProcessKey(
                        secondProcessDefinition.getBpmnProcessId(), DEFAULT_TENANT_ID))
                .size())
        .isEqualTo(1);
  }

  @Test
  public void testGetProcessesIdsToProcessesWithFields() {
    final Map<Long, ProcessEntity> results =
        processStore.getProcessesIdsToProcessesWithFields(
            Set.of(
                firstProcessDefinition.getBpmnProcessId(),
                secondProcessDefinition.getBpmnProcessId()),
            10,
            "name",
            "bpmnProcessId",
            "key");
    assertThat(results.size()).isEqualTo(2);
    assertThat(results.get(firstProcessDefinition.getKey()).getBpmnProcessId())
        .isEqualTo(firstProcessDefinition.getBpmnProcessId());
    assertThat(results.get(secondProcessDefinition.getKey()).getBpmnProcessId())
        .isEqualTo(secondProcessDefinition.getBpmnProcessId());
  }

  @Test
  public void testGetProcessInstanceListViewByKey() {
    final ProcessInstanceForListViewEntity result =
        processStore.getProcessInstanceListViewByKey(thirdProcessInstance.getProcessInstanceKey());

    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(thirdProcessInstance.getBpmnProcessId());
    assertThat(result.getKey()).isEqualTo(thirdProcessInstance.getProcessInstanceKey());
  }

  @Test
  public void testGetCoreStatistics() {
    final Map<String, Long> results = processStore.getCoreStatistics(Set.of("demoProcess-1"));
    assertThat(results.size()).isEqualTo(2);
    assertThat(results.get("running")).isEqualTo(1);
    assertThat(results.get("incidents")).isEqualTo(1);
  }

  @Test
  public void testGetProcessInstanceTreePathById() {
    final String result = processStore.getProcessInstanceTreePathById(thirdProcessInstance.getId());
    assertThat(result).isEqualTo(thirdProcessInstance.getTreePath());
  }

  @Test
  public void testDeleteDocument() throws IOException {
    final Long processKey = 2951799813685251L;
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        new ProcessInstanceForListViewEntity()
            .setKey(processKey)
            .setId(String.valueOf(processKey))
            .setBpmnProcessId("fakeProcess")
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-list*");

    final long deleted =
        processStore.deleteDocument(
            listViewTemplate.getFullQualifiedName(),
            ListViewTemplate.KEY,
            String.valueOf(processKey));

    searchContainerManager.refreshIndices("*operate-list*");

    final var response =
        testSearchRepository.searchTerm(
            listViewTemplate.getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            processKey,
            Object.class,
            1);
    assertThat(response.size()).isZero();
    assertThat(deleted).isEqualTo(1L);
  }

  @Test
  public void testGetProcessInstancesByProcessAndStates() {
    final List<ProcessInstanceForListViewEntity> results =
        processStore.getProcessInstancesByProcessAndStates(
            secondProcessDefinition.getKey(), Set.of(ProcessInstanceState.COMPLETED), 10, null);

    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0).getProcessDefinitionKey())
        .isEqualTo(secondProcessDefinition.getKey());
    assertThat(results.get(0).getState()).isEqualTo(ProcessInstanceState.COMPLETED);
  }

  @Test
  public void testGetProcessInstancesByProcessAndStatesWithMultipleStates() {
    final List<ProcessInstanceForListViewEntity> results =
        processStore.getProcessInstancesByProcessAndStates(
            secondProcessDefinition.getKey(),
            Set.of(ProcessInstanceState.COMPLETED, ProcessInstanceState.ACTIVE),
            10,
            null);

    assertThat(results.size()).isEqualTo(2);
    assertThat(
            results.stream()
                .filter(
                    data ->
                        data.getProcessDefinitionKey().equals(secondProcessDefinition.getKey())
                            && data.getState().equals(ProcessInstanceState.COMPLETED))
                .findAny()
                .orElse(null))
        .isNotNull();
    assertThat(
            results.stream()
                .filter(
                    data ->
                        data.getProcessDefinitionKey().equals(secondProcessDefinition.getKey())
                            && data.getState().equals(ProcessInstanceState.ACTIVE))
                .findAny()
                .orElse(null))
        .isNotNull();
  }

  @Test
  public void testGetProcessInstancesByParentKeys() {
    final List<ProcessInstanceForListViewEntity> results =
        processStore.getProcessInstancesByParentKeys(
            Set.of(secondProcessInstance.getParentProcessInstanceKey()), 10, null);

    assertThat(results.size()).isEqualTo(2);
    assertThat(
            results.stream()
                .filter(
                    data ->
                        data.getProcessInstanceKey()
                                .equals(secondProcessInstance.getProcessInstanceKey())
                            && data.getParentProcessInstanceKey()
                                .equals(secondProcessInstance.getParentProcessInstanceKey()))
                .findAny()
                .orElse(null))
        .isNotNull();
    assertThat(
            results.stream()
                .filter(
                    data ->
                        data.getProcessInstanceKey()
                                .equals(thirdProcessInstance.getProcessInstanceKey())
                            && data.getParentProcessInstanceKey()
                                .equals(thirdProcessInstance.getParentProcessInstanceKey()))
                .findAny()
                .orElse(null))
        .isNotNull();
  }

  @Test
  public void testDeleteProcessDefinitionsByKeys() throws IOException {
    testSearchRepository.createOrUpdateDocumentFromObject(
        processDefinitionIndex.getFullQualifiedName(),
        new ProcessEntity()
            .setKey(2251799813685298L)
            .setBpmnProcessId("testProcess1")
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("Test process 1"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        processDefinitionIndex.getFullQualifiedName(),
        new ProcessEntity()
            .setKey(2251799813685299L)
            .setBpmnProcessId("testProcess2")
            .setTenantId(DEFAULT_TENANT_ID)
            .setName("Test process 2"));
    searchContainerManager.refreshIndices("*operate-process*");

    final long deleted =
        processStore.deleteProcessDefinitionsByKeys(2251799813685298L, 2251799813685299L);
    assertThat(deleted).isEqualTo(2);

    searchContainerManager.refreshIndices("*operate-process*");
  }

  @Test
  public void testDeleteProcessInstancesAndDependants() throws IOException {
    final Long processKey = 2951799813685251L;
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        new ProcessInstanceForListViewEntity()
            .setKey(processKey)
            .setId(String.valueOf(processKey))
            .setBpmnProcessId("fakeProcess")
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstanceEntity().setProcessInstanceKey(processKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlowEntity().setProcessInstanceKey(processKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new VariableEntity().setProcessInstanceKey(processKey));

    searchContainerManager.refreshIndices("*");

    final long deleted = processStore.deleteProcessInstancesAndDependants(Set.of(processKey));

    searchContainerManager.refreshIndices("*");
    final var response =
        testSearchRepository.searchTerm(
            listViewTemplate.getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            processKey,
            Object.class,
            1);
    assertThat(response.size()).isZero();
    assertThat(deleted).isEqualTo(4);
  }

  private String getFullIndexNameForDependant(String indexName) {
    final ProcessInstanceDependant dependant =
        processInstanceDependantTemplates.stream()
            .filter(template -> template.getFullQualifiedName().contains(indexName))
            .findAny()
            .orElse(null);

    return dependant.getFullQualifiedName();
  }
}
