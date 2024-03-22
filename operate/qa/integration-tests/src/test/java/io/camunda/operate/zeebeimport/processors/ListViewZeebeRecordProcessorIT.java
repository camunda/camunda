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
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.v8_5.processors.ListViewZeebeRecordProcessor;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord.Builder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
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

  final int newVersion = 111;
  final String newBpmnProcessId = "newBpmnProcessId";
  final long newProcessDefinitionKey = 111;
  final String newProcessName = "New process name";
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
  @MockBean private ProcessCache processCache;
  @Autowired private ImportPositionHolder importPositionHolder;
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
  public void shouldOverrideFields() throws IOException, PersistenceException {
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
    importZeebeRecord(pi, zeebeRecord);

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
  public void shouldNotOverrideFields() throws IOException, PersistenceException {
    // having
    // process instance entity with position = 2
    final long oldPosition = 2L;
    final ProcessInstanceForListViewEntity pi = createProcessInstance().setPosition(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), pi.getId(), pi);

    // when
    // importing Zeebe record with bigger position
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
    importZeebeRecord(pi, zeebeRecord);

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

  @NotNull
  private ProcessInstanceForListViewEntity findProcessInstanceByKey(final long key)
      throws IOException {
    final List<ProcessInstanceForListViewEntity> entities =
        testSearchRepository.searchAll(
            listViewTemplate.getFullQualifiedName(), ProcessInstanceForListViewEntity.class);
    final Optional<ProcessInstanceForListViewEntity> first =
        entities.stream().filter(p -> p.getKey() == key).findFirst();
    assertThat(first.isPresent()).isTrue();
    final ProcessInstanceForListViewEntity updatedPI = first.get();
    return updatedPI;
  }

  private void importZeebeRecord(
      final ProcessInstanceForListViewEntity pi,
      final Record<ProcessInstanceRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    listViewZeebeRecordProcessor.processProcessInstanceRecord(
        (Map) Map.of(pi.getKey(), List.of(zeebeRecord)), batchRequest, mock(ImportBatch.class));
    batchRequest.execute();
    // FIXME can we avoid that? Refresh indices?
    ThreadUtil.sleepFor(2000L);
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
}
