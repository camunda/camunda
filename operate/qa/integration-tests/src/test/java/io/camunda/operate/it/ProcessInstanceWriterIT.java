/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.it;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.ListViewJoinRelation;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceWriterIT extends OperateSearchAbstractIT {

  @Autowired private ProcessInstanceWriter processInstanceWriter;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired private ListViewTemplate listViewTemplate;

  @Override
  public void runAdditionalBeforeAllSetup() {
    // operateTester.deployProcessAndWait("single-task.bpmn");
  }

  @Test
  public void shouldDeleteFinishedInstanceById() throws IOException {
    Long processInstanceKey = 4503599627370497L;
    ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId(String.valueOf(processInstanceKey))
            .setKey(processInstanceKey)
            .setProcessDefinitionKey(2251799813685248L)
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.COMPLETED)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setTreePath("PI_4503599627370497")
            .setTenantId(DEFAULT_TENANT_ID)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstanceEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlowEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new VariableEntity().setProcessInstanceKey(processInstanceKey));

    searchContainerManager.refreshIndices("*");

    processInstanceWriter.deleteInstanceById(processInstance.getProcessInstanceKey());

    searchContainerManager.refreshIndices("*");

    assertThrows(
        NotFoundException.class,
        () ->
            processInstanceReader.getProcessInstanceByKey(processInstance.getProcessInstanceKey()));
    assertThatDependantsAreAlsoDeleted(processInstanceKey);
  }

  @Test
  public void shouldDeleteCancelledInstanceById() throws IOException {
    Long processInstanceKey = 4503599627370497L;
    ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId(String.valueOf(processInstanceKey))
            .setKey(processInstanceKey)
            .setProcessDefinitionKey(2251799813685248L)
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.CANCELED)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setTreePath("PI_4503599627370497")
            .setTenantId(DEFAULT_TENANT_ID)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstanceEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlowEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new VariableEntity().setProcessInstanceKey(processInstanceKey));

    searchContainerManager.refreshIndices("*");

    processInstanceWriter.deleteInstanceById(processInstanceKey);

    searchContainerManager.refreshIndices("*");

    assertThrows(
        NotFoundException.class,
        () -> processInstanceReader.getProcessInstanceByKey(processInstanceKey));
    assertThatDependantsAreAlsoDeleted(processInstanceKey);
  }

  @Test
  public void shouldFailDeleteInstanceByIdWithInvalidState() throws IOException {
    Long processInstanceKey = 4503599627370497L;
    ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId(String.valueOf(processInstanceKey))
            .setKey(processInstanceKey)
            .setProcessDefinitionKey(2251799813685248L)
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.ACTIVE)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setTreePath("PI_4503599627370497")
            .setTenantId(DEFAULT_TENANT_ID)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*");

    assertThrows(
        IllegalArgumentException.class,
        () -> processInstanceWriter.deleteInstanceById(processInstanceKey));

    // Cleanup so as not to interfere with other tests
    processInstance.setState(ProcessInstanceState.COMPLETED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*");
    processInstanceWriter.deleteInstanceById(processInstanceKey);
  }

  private void assertThatDependantsAreAlsoDeleted(final long finishedProcessInstanceKey)
      throws IOException {
    for (ProcessInstanceDependant t : processInstanceDependants) {
      if (!(t instanceof OperationTemplate)) {
        var index = t.getFullQualifiedName() + "*";
        var field = ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
        var response =
            testSearchRepository.searchTerm(
                index, field, finishedProcessInstanceKey, Object.class, 100);
        assertThat(response.size()).isZero();
      }
    }
  }

  private String getFullIndexNameForDependant(String indexName) {
    ProcessInstanceDependant dependant =
        processInstanceDependants.stream()
            .filter(template -> template.getFullQualifiedName().contains(indexName))
            .findAny()
            .orElse(null);

    return dependant.getFullQualifiedName();
  }
}
