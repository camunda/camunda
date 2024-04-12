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
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.entities.listview.ProcessInstanceState.ACTIVE;
import static io.camunda.operate.entities.listview.ProcessInstanceState.COMPLETED;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.VARIABLES_JOIN_RELATION;
import static io.camunda.operate.util.TestUtil.createFlowNodeInstance;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static io.camunda.operate.util.TestUtil.createVariableForListView;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.v8_5.processors.ListViewZeebeRecordProcessor;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord.Builder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ListViewZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  private final int newVersion = 111;
  private final String newBpmnProcessId = "newBpmnProcessId";
  private final long newProcessDefinitionKey = 111;
  private final String newProcessName = "New process name";
  private final String errorMessage = "Error message";
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
  @MockBean private ProcessCache processCache;
  @Autowired private ImportPositionHolder importPositionHolder;
  @Autowired private OperateProperties operateProperties;
  private boolean concurrencyModeBefore;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1));
    concurrencyModeBefore = importPositionHolder.getConcurrencyMode();
    importPositionHolder.setConcurrencyMode(true);
  }

  @Override
  @AfterAll
  public void afterAllTeardown() {
    importPositionHolder.setConcurrencyMode(concurrencyModeBefore);
    super.afterAllTeardown();
  }

  @Test
  public void shouldOverrideProcessInstanceFields() throws IOException, PersistenceException {
    // having
    // process instance entity with position = 1
    final ProcessInstanceForListViewEntity pi = createProcessInstance().setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), pi.getId(), pi);

    // when
    // importing Zeebe record with bigger position
    when(processCache.getProcessNameOrDefaultValue(eq(newProcessDefinitionKey), anyString()))
        .thenReturn(newProcessName);
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromPi(
            pi,
            b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED),
            b ->
                b.withVersion(newVersion)
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final ProcessInstanceForListViewEntity updatedPI = findProcessInstanceByKey(pi.getKey());
    // old values
    assertThat(updatedPI.getProcessInstanceKey()).isEqualTo(pi.getProcessInstanceKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getKey()).isEqualTo(pi.getKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getStartDate()).isNotNull();
    // new values
    assertThat(updatedPI.getProcessName()).isEqualTo(newProcessName);
    assertThat(updatedPI.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedPI.getProcessVersion()).isEqualTo(newVersion);
    assertThat(updatedPI.getState()).isEqualTo(COMPLETED);
    assertThat(updatedPI.getEndDate()).isNotNull();
    assertThat(updatedPI.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideProcessInstanceFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // process instance entity with null position
    final ProcessInstanceForListViewEntity pi = createProcessInstance(); // null positions field
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), pi.getId(), pi);

    // when
    // importing Zeebe record with bigger position
    when(processCache.getProcessNameOrDefaultValue(eq(newProcessDefinitionKey), anyString()))
        .thenReturn(newProcessName);
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromPi(
            pi,
            b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED),
            b ->
                b.withVersion(newVersion)
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final ProcessInstanceForListViewEntity updatedPI = findProcessInstanceByKey(pi.getKey());
    // old values
    assertThat(updatedPI.getProcessInstanceKey()).isEqualTo(pi.getProcessInstanceKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getKey()).isEqualTo(pi.getKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getStartDate()).isNotNull();
    // new values
    assertThat(updatedPI.getProcessName()).isEqualTo(newProcessName);
    assertThat(updatedPI.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedPI.getProcessVersion()).isEqualTo(newVersion);
    assertThat(updatedPI.getState()).isEqualTo(COMPLETED);
    assertThat(updatedPI.getEndDate()).isNotNull();
    assertThat(updatedPI.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideProcessInstanceFields() throws IOException, PersistenceException {
    // having
    // process instance entity with position = 2
    final long oldPosition = 2L;
    final ProcessInstanceForListViewEntity pi = createProcessInstance().setPosition(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), pi.getId(), pi);

    // when
    // importing Zeebe record with smaller position
    when(processCache.getProcessNameOrDefaultValue(eq(newProcessDefinitionKey), anyString()))
        .thenReturn(newProcessName);
    final long newPosition = 1L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromPi(
            pi,
            b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED),
            b ->
                b.withVersion(newVersion)
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final ProcessInstanceForListViewEntity updatedPI = findProcessInstanceByKey(pi.getKey());
    // old values
    assertThat(updatedPI.getProcessInstanceKey()).isEqualTo(pi.getProcessInstanceKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getKey()).isEqualTo(pi.getKey());
    assertThat(updatedPI.getTenantId()).isEqualTo(pi.getTenantId());
    assertThat(updatedPI.getStartDate()).isNotNull();
    // old values
    assertThat(updatedPI.getProcessName()).isEqualTo(pi.getProcessName());
    assertThat(updatedPI.getProcessDefinitionKey()).isEqualTo(pi.getProcessDefinitionKey());
    assertThat(updatedPI.getProcessVersion()).isEqualTo(pi.getProcessVersion());
    assertThat(updatedPI.getState()).isEqualTo(ACTIVE);
    assertThat(updatedPI.getEndDate()).isNull();
    assertThat(updatedPI.getPosition()).isEqualTo(oldPosition);
  }

  @Test
  public void shouldOverrideIncidentErrorMsg() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 1
    final long processInstanceKey = 111L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPositionIncident(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<IncidentRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(112L)
                .withPosition(newPosition)
                .withIntent(CREATED)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withErrorMessage(errorMessage)
                        .build())
                .build();
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(updatedFni.getPositionIncident()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideIncidentErrorMsgForNullPosition()
      throws IOException, PersistenceException {
    // having
    // flow node instance entity with null position
    final long processInstanceKey = 111L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE); // null positions field
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<IncidentRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(112L)
                .withPosition(newPosition)
                .withIntent(CREATED)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withErrorMessage(errorMessage)
                        .build())
                .build();
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(updatedFni.getPositionIncident()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideIncidentErrorMsg() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 2
    final long processInstanceKey = 111L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPositionIncident(2L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final Record<IncidentRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(112L)
                .withPosition(newPosition)
                .withIntent(CREATED)
                .withValue(
                    ImmutableIncidentRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withErrorMessage(errorMessage)
                        .build())
                .build();
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // incident fields are not updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    assertThat(updatedFni.getErrorMessage()).isNull();
    assertThat(updatedFni.getPositionIncident()).isEqualTo(fni.getPositionIncident());
  }

  @Test
  public void shouldOverrideVariableFields() throws IOException, PersistenceException {
    // having
    // variable entity with position = 1
    final long processInstanceKey = 111L;
    final VariableForListViewEntity var =
        createVariableForListView(processInstanceKey).setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        var.getId(),
        var,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final String newValue = "newValue";
    final Record<VariableRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(113L)
                .withPosition(newPosition)
                .withIntent(VariableIntent.UPDATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .withName(var.getVarName())
                        .withValue(newValue)
                        .withScopeKey(var.getScopeKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .build())
                .build();
    importVariableZeebeRecord(zeebeRecord);

    // then
    // variable fields are updated
    final VariableForListViewEntity updatedVar = variableById(var.getId());
    // old values
    assertThat(updatedVar.getId()).isEqualTo(var.getId());
    assertThat(updatedVar.getVarName()).isEqualTo(var.getVarName());
    // new values
    assertThat(updatedVar.getVarValue()).isEqualTo(newValue);
  }

  @Test
  public void shouldOverrideVariableFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // variable entity with null position
    final long processInstanceKey = 111L;
    final VariableForListViewEntity var = createVariableForListView(processInstanceKey);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        var.getId(),
        var,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final String newValue = "newValue";
    final Record<VariableRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(113L)
                .withPosition(newPosition)
                .withIntent(VariableIntent.UPDATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .withName(var.getVarName())
                        .withValue(newValue)
                        .withScopeKey(var.getScopeKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .build())
                .build();
    importVariableZeebeRecord(zeebeRecord);

    // then
    // variable fields are updated
    final VariableForListViewEntity updatedVar = variableById(var.getId());
    // old values
    assertThat(updatedVar.getId()).isEqualTo(var.getId());
    assertThat(updatedVar.getVarName()).isEqualTo(var.getVarName());
    // new values
    assertThat(updatedVar.getVarValue()).isEqualTo(newValue);
  }

  @Test
  public void shouldNotOverrideVariableFields() throws IOException, PersistenceException {
    // having
    // process instance entity with position = 2
    final long processInstanceKey = 111L;
    final VariableForListViewEntity var =
        createVariableForListView(processInstanceKey).setPosition(2L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        var.getId(),
        var,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 1L;
    final String newValue = "newValue";
    final Record<VariableRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(113L)
                .withPosition(newPosition)
                .withIntent(VariableIntent.UPDATED)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .withName(var.getVarName())
                        .withValue(newValue)
                        .withScopeKey(var.getScopeKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .build())
                .build();
    importVariableZeebeRecord(zeebeRecord);

    // then
    // variable fields are not updated
    final VariableForListViewEntity updatedVar = variableById(var.getId());
    // old values
    assertThat(updatedVar.getId()).isEqualTo(var.getId());
    assertThat(updatedVar.getVarName()).isEqualTo(var.getVarName());
    assertThat(updatedVar.getVarValue()).isEqualTo(var.getVarValue());
  }

  @Test
  public void shouldOverrideFlowNodeInstanceFields() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 1
    final long processInstanceKey = 222L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromFni(
            fni, b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED), null);
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.getActivityState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(updatedFni.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideFlowNodeInstanceFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // flow node instance entity with null position
    final long processInstanceKey = 222L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromFni(
            fni, b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED), null);
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.getActivityState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(updatedFni.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideFlowNodeInstanceFields() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 2
    final long processInstanceKey = 222L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPosition(2L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 1L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createZeebeRecordFromFni(
            fni, b -> b.withPosition(newPosition).withIntent(ELEMENT_COMPLETED), null);
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    assertThat(updatedFni.getActivityState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(updatedFni.getPosition()).isEqualTo(2L);
  }

  @Test
  public void shouldOverrideJobFailedWithRetriesField() throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 1
    final long processInstanceKey = 333L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPositionJob(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<JobRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(114L)
                .withPosition(newPosition)
                .withIntent(JobIntent.FAILED)
                .withValue(
                    ImmutableJobRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .withRetries(1)
                        .build())
                .build();
    importJobZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.isJobFailedWithRetriesLeft()).isEqualTo(true);
    assertThat(updatedFni.getPositionJob()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideJobFailedWithRetriesForNullPosition()
      throws IOException, PersistenceException {
    // having
    // flow node instance entity with null position
    final long processInstanceKey = 333L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<JobRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(115L)
                .withPosition(newPosition)
                .withIntent(JobIntent.FAILED)
                .withValue(
                    ImmutableJobRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .withRetries(1)
                        .build())
                .build();
    importJobZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    // new values
    assertThat(updatedFni.isJobFailedWithRetriesLeft()).isEqualTo(true);
    assertThat(updatedFni.getPositionJob()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideJobFailedWithRetriesField()
      throws IOException, PersistenceException {
    // having
    // flow node instance entity with position = 2
    final long processInstanceKey = 333L;
    final FlowNodeInstanceForListViewEntity fni =
        createFlowNodeInstance(processInstanceKey, FlowNodeState.ACTIVE).setPositionJob(2L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        fni.getId(),
        fni,
        String.valueOf(processInstanceKey));

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 1L;
    final Record<JobRecordValue> zeebeRecord =
        (Record)
            ImmutableRecord.builder()
                .withKey(115L)
                .withPosition(newPosition)
                .withIntent(JobIntent.FAILED)
                .withValue(
                    ImmutableJobRecordValue.builder()
                        .withElementInstanceKey(fni.getKey())
                        .withProcessInstanceKey(processInstanceKey)
                        .withRetries(1)
                        .build())
                .build();
    importJobZeebeRecord(zeebeRecord);

    // then
    // incident fields are updated
    final FlowNodeInstanceForListViewEntity updatedFni = findFlowNodeInstanceByKey(fni.getKey());
    // old values
    assertThat(updatedFni.getKey()).isEqualTo(fni.getKey());
    assertThat(updatedFni.isJobFailedWithRetriesLeft()).isEqualTo(false);
    assertThat(updatedFni.getPositionJob()).isEqualTo(2L);
  }

  @NotNull
  private ProcessInstanceForListViewEntity findProcessInstanceByKey(final long key)
      throws IOException {
    final List<ProcessInstanceForListViewEntity> entities =
        testSearchRepository.searchJoinRelation(
            listViewTemplate.getFullQualifiedName(),
            PROCESS_INSTANCE_JOIN_RELATION,
            ProcessInstanceForListViewEntity.class,
            10);
    final Optional<ProcessInstanceForListViewEntity> first =
        entities.stream().filter(p -> p.getKey() == key).findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  @NotNull
  private FlowNodeInstanceForListViewEntity findFlowNodeInstanceByKey(final long key)
      throws IOException {
    final List<FlowNodeInstanceForListViewEntity> entities =
        testSearchRepository.searchJoinRelation(
            listViewTemplate.getFullQualifiedName(),
            ACTIVITIES_JOIN_RELATION,
            FlowNodeInstanceForListViewEntity.class,
            10);
    final Optional<FlowNodeInstanceForListViewEntity> first =
        entities.stream().filter(p -> p.getKey() == key).findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  @NotNull
  private VariableForListViewEntity variableById(final String id) throws IOException {
    final List<VariableForListViewEntity> entities =
        testSearchRepository.searchJoinRelation(
            listViewTemplate.getFullQualifiedName(),
            VARIABLES_JOIN_RELATION,
            VariableForListViewEntity.class,
            10);
    final Optional<VariableForListViewEntity> first =
        entities.stream().filter(p -> p.getId().equals(id)).findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private void importProcessInstanceZeebeRecord(
      final Record<ProcessInstanceRecordValue> zeebeRecord) throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processProcessInstanceRecord(
        (Map) Map.of(zeebeRecord.getKey(), List.of(zeebeRecord)),
        batchRequest,
        mock(ImportBatch.class));
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }

  private void importIncidentZeebeRecord(final Record<IncidentRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processIncidentRecord(zeebeRecord, batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }

  private void importVariableZeebeRecord(final Record<VariableRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processVariableRecords(
        (Map) Map.of(zeebeRecord.getKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }

  private void importJobZeebeRecord(final Record<JobRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processJobRecords(
        (Map) Map.of(zeebeRecord.getKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(listViewTemplate.getFullQualifiedName());
  }

  @NotNull
  private static Record<ProcessInstanceRecordValue> createZeebeRecordFromPi(
      final ProcessInstanceForListViewEntity pi,
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableProcessInstanceRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<ProcessInstanceRecordValue> builder = ImmutableRecord.builder();
    final ImmutableProcessInstanceRecordValue.Builder valueBuilder =
        ImmutableProcessInstanceRecordValue.builder();
    valueBuilder
        .withProcessInstanceKey(pi.getProcessInstanceKey())
        .withBpmnProcessId(pi.getBpmnProcessId())
        .withBpmnElementType(BpmnElementType.PROCESS)
        .withProcessDefinitionKey(pi.getProcessDefinitionKey())
        .withVersion(pi.getProcessVersion())
        .withTenantId(pi.getTenantId());
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withKey(pi.getKey())
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build());
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }

  @NotNull
  private static Record<ProcessInstanceRecordValue> createZeebeRecordFromFni(
      final FlowNodeInstanceForListViewEntity fni,
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableProcessInstanceRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<ProcessInstanceRecordValue> builder = ImmutableRecord.builder();
    final ImmutableProcessInstanceRecordValue.Builder valueBuilder =
        ImmutableProcessInstanceRecordValue.builder();
    valueBuilder
        .withBpmnElementType(BpmnElementType.valueOf(fni.getActivityType().toString()))
        .withElementId(fni.getActivityId())
        .withProcessInstanceKey(fni.getProcessInstanceKey())
        .withTenantId(fni.getTenantId());
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withKey(fni.getKey())
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build());
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }
}
