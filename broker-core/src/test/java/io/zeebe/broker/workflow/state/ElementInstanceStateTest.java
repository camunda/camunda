/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.state;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.workflow.state.StoredRecord.Purpose;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.AutoCloseableRule;
import java.util.List;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ElementInstanceStateTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();

  private ElementInstanceState elementInstanceState;

  @Before
  public void setUp() throws Exception {
    final WorkflowState workflowState = new WorkflowState();
    workflowState.open(folder.newFolder("rocksdb"), false);
    elementInstanceState = workflowState.getElementInstanceState();
    closeables.manage(workflowState);
  }

  @Test
  public void shouldCreateNewInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();

    // when
    final ElementInstance elementInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // then
    assertElementInstance(elementInstance, 0);
  }

  @Test
  public void shouldCreateNewInstanceWithParent() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final WorkflowInstanceRecord otherRecord = createWorkflowInstanceRecord();
    otherRecord.setElementId("subProcess");
    otherRecord.setPayload(asMsgPack("foo", "bar"));
    final ElementInstance childInstance =
        elementInstanceState.newInstance(
            parentInstance, 101, otherRecord, WorkflowInstanceIntent.ELEMENT_READY);

    // then
    assertElementInstance(parentInstance, 1);
    assertChildInstance(childInstance, 101, "subProcess");
  }

  @Test
  public void shouldSpawnToken() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    elementInstanceState.spawnToken(100);

    // then
    final ElementInstance elementInstance = elementInstanceState.getInstance(100);
    assertThat(elementInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    assertThat(elementInstance.getNumberOfActiveExecutionPaths()).isEqualTo(1);
    assertThat(elementInstance.getNumberOfActiveTokens()).isEqualTo(1);
  }

  @Test
  public void shouldConsumeToken() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.spawnToken(100);

    // when
    elementInstanceState.consumeToken(100);

    // then
    final ElementInstance elementInstance = elementInstanceState.getInstance(100);
    assertThat(elementInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    assertThat(elementInstance.getNumberOfActiveExecutionPaths()).isEqualTo(0);
    assertThat(elementInstance.getNumberOfActiveTokens()).isEqualTo(0);
  }

  @Test
  public void shouldFindElementInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final ElementInstance instance = elementInstanceState.getInstance(100);

    // then
    assertElementInstance(instance, 0);
  }

  @Test
  public void shouldFindChildInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_READY);

    // when
    final ElementInstance childInstance = elementInstanceState.getInstance(101L);

    // then
    assertChildInstance(childInstance, 101, "subProcess");
  }

  @Test
  public void shouldFindParentInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_READY);

    // when
    final ElementInstance updatedParentInstance = elementInstanceState.getInstance(100L);

    // then
    assertElementInstance(updatedParentInstance, 1);
  }

  @Test
  public void shouldRemoveParentInstanceAfterRemovingChild() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_READY);
    elementInstanceState.flushDirtyState();
    elementInstanceState.removeInstance(101L);

    // when
    elementInstanceState.removeInstance(100L);

    // then
    final ElementInstance childInstance = elementInstanceState.getInstance(101L);
    assertThat(childInstance).isNull();

    final ElementInstance parent = elementInstanceState.getInstance(100L);
    assertThat(parent).isNull();

    final List<ElementInstance> children = elementInstanceState.getChildren(100L);
    assertThat(children).hasSize(0);
  }

  @Test
  public void shouldRemoveChildInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_READY);
    workflowInstanceRecord.setElementId("subProcess2");
    elementInstanceState.newInstance(
        parentInstance, 102, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_READY);
    elementInstanceState.flushDirtyState();

    // when
    elementInstanceState.removeInstance(101L);

    // then
    final ElementInstance childInstance = elementInstanceState.getInstance(101L);
    assertThat(childInstance).isNull();

    final List<ElementInstance> children = elementInstanceState.getChildren(100L);
    assertThat(children).hasSize(1);

    final ElementInstance childInstance2 = elementInstanceState.getInstance(102L);
    assertChildInstance(childInstance2, 102L, "subProcess2");

    final ElementInstance updatedParent = elementInstanceState.getInstance(100L);
    assertElementInstance(updatedParent, 1);
  }

  @Test
  public void shouldUpdateElementInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.spawnToken();
    instance.setState(WorkflowInstanceIntent.ELEMENT_READY);
    instance.setJobKey(5);
    elementInstanceState.flushDirtyState();

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);

    assertThat(updatedInstance.getKey()).isEqualTo(100);
    assertThat(updatedInstance.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_READY);
    assertThat(updatedInstance.getJobKey()).isEqualTo(5);
    assertThat(updatedInstance.canTerminate()).isTrue();

    assertThat(updatedInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    assertThat(updatedInstance.getNumberOfActiveExecutionPaths()).isEqualTo(1);
    assertThat(updatedInstance.getNumberOfActiveTokens()).isEqualTo(1);

    final WorkflowInstanceRecord record = updatedInstance.getValue();
    assertWorkflowInstanceRecord(record);
  }

  @Test
  public void shouldUpdateElementInstanceInCache() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    instance.spawnToken();
    instance.setState(WorkflowInstanceIntent.ELEMENT_READY);
    instance.setJobKey(5);

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);

    assertThat(updatedInstance.getKey()).isEqualTo(100);
    assertThat(updatedInstance.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_READY);
    assertThat(updatedInstance.getJobKey()).isEqualTo(5);
    assertThat(updatedInstance.canTerminate()).isTrue();

    assertThat(updatedInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    assertThat(updatedInstance.getNumberOfActiveExecutionPaths()).isEqualTo(1);
    assertThat(updatedInstance.getNumberOfActiveTokens()).isEqualTo(1);

    final WorkflowInstanceRecord record = updatedInstance.getValue();
    assertWorkflowInstanceRecord(record);
  }

  @Test
  public void shouldNotUpdateElementInstanceWithoutFlush() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.flushDirtyState();

    // when
    instance.spawnToken();
    instance.setState(WorkflowInstanceIntent.ELEMENT_READY);
    instance.setJobKey(5);

    // then
    final ElementInstance oldInstance = elementInstanceState.getInstance(100);

    assertElementInstance(oldInstance, 0);
  }

  @Test
  public void shouldUpdateValueOfElementInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final DirectBuffer payload = asMsgPack("foo", "bar");
    instance.getValue().setPayload(payload);
    elementInstanceState.flushDirtyState();

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);

    final WorkflowInstanceRecord value = updatedInstance.getValue();
    assertThat(value.getPayload()).isEqualTo(payload);
  }

  @Test
  public void shouldReplaceValueOfElementInstance() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance instance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final WorkflowInstanceRecord otherRecord = createWorkflowInstanceRecord();
    final DirectBuffer payload = asMsgPack("foo", "bar");
    otherRecord.setPayload(payload);
    instance.setValue(otherRecord);
    elementInstanceState.flushDirtyState();

    // then
    final ElementInstance updatedInstance = elementInstanceState.getInstance(100);

    final WorkflowInstanceRecord value = updatedInstance.getValue();
    assertThat(value.getPayload()).isEqualTo(payload);
  }

  @Test
  public void shouldCollectChildInstances() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    final ElementInstance parentInstance =
        elementInstanceState.newInstance(
            100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    workflowInstanceRecord.setElementId("subProcess");
    elementInstanceState.newInstance(
        parentInstance, 101, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_READY);
    workflowInstanceRecord.setElementId("subProcess2");
    elementInstanceState.newInstance(
        parentInstance, 102, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_READY);
    elementInstanceState.flushDirtyState();

    // when
    final List<ElementInstance> children = elementInstanceState.getChildren(100L);

    // then
    assertThat(children).hasSize(2);
    assertChildInstance(children.get(0), 101, "subProcess");
    assertChildInstance(children.get(1), 102, "subProcess2");
  }

  @Test
  public void shouldStoreAndCollectRecord() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final TypedRecord<WorkflowInstanceRecord> typedRecord = mockTypedRecord();
    elementInstanceState.storeTokenEvent(100, typedRecord, Purpose.DEFERRED_TOKEN);

    // then
    final List<IndexedRecord> storedRecords = elementInstanceState.getDeferredTokens(100);

    assertThat(storedRecords).hasSize(1);
    final IndexedRecord indexedRecord = storedRecords.get(0);
    assertThat(indexedRecord.getKey()).isEqualTo(123L);
    assertThat(indexedRecord.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    assertWorkflowInstanceRecord(indexedRecord.getValue());
  }

  @Test
  public void shouldStoreAndCollectMultipleRecords() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final TypedRecord<WorkflowInstanceRecord> typedRecord = mockTypedRecord(123L);
    elementInstanceState.storeTokenEvent(100, typedRecord, Purpose.DEFERRED_TOKEN);
    final TypedRecord<WorkflowInstanceRecord> typedRecord2 = mockTypedRecord(124L);
    elementInstanceState.storeTokenEvent(100, typedRecord2, Purpose.FINISHED_TOKEN);

    // then
    final List<IndexedRecord> deferredTokens = elementInstanceState.getDeferredTokens(100);
    final List<IndexedRecord> finishedTokens = elementInstanceState.getFinishedTokens(100);

    assertThat(deferredTokens).hasSize(1);
    final IndexedRecord deferredToken = deferredTokens.get(0);
    assertThat(deferredToken.getKey()).isEqualTo(123L);
    assertThat(deferredToken.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(finishedTokens).hasSize(1);
    final IndexedRecord finishedToken = finishedTokens.get(0);
    assertThat(finishedToken.getKey()).isEqualTo(124L);
    assertThat(finishedToken.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
  }

  @Test
  public void shouldRemoveSingleRecord() {
    // given
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        100, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final TypedRecord<WorkflowInstanceRecord> typedRecord = mockTypedRecord(123L);
    elementInstanceState.storeTokenEvent(100, typedRecord, Purpose.DEFERRED_TOKEN);
    final TypedRecord<WorkflowInstanceRecord> typedRecord2 = mockTypedRecord(124L);
    elementInstanceState.storeTokenEvent(100, typedRecord2, Purpose.DEFERRED_TOKEN);

    // when
    elementInstanceState.removeStoredRecord(100, 123, Purpose.DEFERRED_TOKEN);

    // then
    final List<IndexedRecord> storedRecords = elementInstanceState.getDeferredTokens(100);

    assertThat(storedRecords).hasSize(1);
    final IndexedRecord indexedRecord = storedRecords.get(0);
    assertThat(indexedRecord.getKey()).isEqualTo(124L);
    assertThat(indexedRecord.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    assertWorkflowInstanceRecord(indexedRecord.getValue());
  }

  @Test
  public void shouldRemoveStoredRecordsOnInstanceRemoval() {
    // given
    final int key = 100;

    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();
    elementInstanceState.newInstance(
        key, workflowInstanceRecord, WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final TypedRecord<WorkflowInstanceRecord> typedRecord = mockTypedRecord(123L);
    elementInstanceState.storeTokenEvent(key, typedRecord, Purpose.DEFERRED_TOKEN);

    // when
    elementInstanceState.removeInstance(key);

    // then
    assertThat(elementInstanceState.getDeferredTokens(key)).isEmpty();
  }

  private TypedRecord<WorkflowInstanceRecord> mockTypedRecord() {
    return mockTypedRecord(123L);
  }

  private TypedRecord<WorkflowInstanceRecord> mockTypedRecord(long key) {
    final WorkflowInstanceRecord workflowInstanceRecord = createWorkflowInstanceRecord();

    final TypedRecord<WorkflowInstanceRecord> typedRecord = mock(TypedRecord.class);
    when(typedRecord.getKey()).thenReturn(key);
    when(typedRecord.getValue()).thenReturn(workflowInstanceRecord);
    final RecordMetadata metadata = new RecordMetadata();
    metadata.intent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    when(typedRecord.getMetadata()).thenReturn(metadata);

    return typedRecord;
  }

  private void assertElementInstance(ElementInstance elementInstance, int childCount) {
    assertThat(elementInstance.getKey()).isEqualTo(100);
    assertThat(elementInstance.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    assertThat(elementInstance.getJobKey()).isEqualTo(0);
    assertThat(elementInstance.canTerminate()).isTrue();

    assertThat(elementInstance.getNumberOfActiveElementInstances()).isEqualTo(childCount);
    assertThat(elementInstance.getNumberOfActiveExecutionPaths()).isEqualTo(childCount);
    assertThat(elementInstance.getNumberOfActiveTokens()).isEqualTo(0);

    final WorkflowInstanceRecord record = elementInstance.getValue();

    assertWorkflowInstanceRecord(record);
  }

  private void assertChildInstance(ElementInstance childInstance, long key, String elementId) {
    assertThat(childInstance.getKey()).isEqualTo(key);
    assertThat(childInstance.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_READY);
    assertThat(childInstance.getJobKey()).isEqualTo(0);
    assertThat(childInstance.canTerminate()).isTrue();

    assertThat(childInstance.getNumberOfActiveElementInstances()).isEqualTo(0);
    assertThat(childInstance.getNumberOfActiveExecutionPaths()).isEqualTo(0);
    assertThat(childInstance.getNumberOfActiveTokens()).isEqualTo(0);

    assertWorkflowInstanceRecord(childInstance.getValue(), wrapString(elementId));
  }

  private WorkflowInstanceRecord createWorkflowInstanceRecord() {
    final WorkflowInstanceRecord workflowInstanceRecord = new WorkflowInstanceRecord();
    workflowInstanceRecord.setElementId("startEvent");
    workflowInstanceRecord.setBpmnProcessId(wrapString("process1"));
    workflowInstanceRecord.setWorkflowInstanceKey(1000L);
    workflowInstanceRecord.setScopeInstanceKey(1001L);
    workflowInstanceRecord.setVersion(1);
    workflowInstanceRecord.setWorkflowKey(2);

    return workflowInstanceRecord;
  }

  private void assertWorkflowInstanceRecord(WorkflowInstanceRecord record) {
    assertWorkflowInstanceRecord(record, wrapString("startEvent"));
  }

  private void assertWorkflowInstanceRecord(WorkflowInstanceRecord record, DirectBuffer elementId) {
    assertThat(record.getElementId()).isEqualTo(elementId);
    assertThat(record.getBpmnProcessId()).isEqualTo(wrapString("process1"));
    assertThat(record.getWorkflowInstanceKey()).isEqualTo(1000L);
    assertThat(record.getScopeInstanceKey()).isEqualTo(1001L);
    assertThat(record.getVersion()).isEqualTo(1);
    assertThat(record.getWorkflowKey()).isEqualTo(2);
  }
}
