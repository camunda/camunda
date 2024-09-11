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
package io.camunda.operate.zeebeimport.processors.fni;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.zeebeimport.cache.FNITreePathCacheCompositeKey;
import io.camunda.operate.zeebeimport.cache.TreePathCache;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class FNITransformerTest {

  private FNITransformer fniTransformer;
  private TreePathCache mockTreePathCache;

  @BeforeEach
  public void setup() {
    mockTreePathCache = Mockito.mock(TreePathCache.class);
    when(mockTreePathCache.resolveParentTreePath(any()))
        .thenAnswer(
            invocationOnMock -> {
              final FNITreePathCacheCompositeKey compositeKey = invocationOnMock.getArgument(0);
              return String.format(
                  "%d/%d", compositeKey.processInstanceKey(), compositeKey.flowScopeKey());
            });
    fniTransformer = new FNITransformer(mockTreePathCache);
  }

  @Test
  public void shouldTransformActivateFNIRecord() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createStartingZeebeRecord(time);

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isEqualTo("1/3/4");
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(2);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNull();
    assertThat(flowNodeInstanceEntity.getStartDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
  }

  @Test
  public void shouldNotCacheLeafFNI() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createStartingZeebeRecord(time);

    // when
    fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    Mockito.verify(mockTreePathCache, times(0)).cacheTreePath(any(), any());
  }

  @Test
  public void shouldCacheContainerFNI() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createStartingZeebeRecord(time);

    // when
    fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    Mockito.verify(mockTreePathCache, times(0)).cacheTreePath(any(), any());
  }

  @Test
  public void shouldTransformMigratedFNIRecord() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createMigratedZeebeRecord(time);

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isEqualTo("1/3/4");
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(2);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNull();
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
  }

  @Test
  public void shouldTransformCompletedFNIRecord() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createCompletedZeebeRecord(time);

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isNull();
    assertThat(flowNodeInstanceEntity.getLevel()).isZero();
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getEndDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
  }

  @Test
  public void shouldTransformMultipleRecordsIntoEntity() {
    // given
    final var time = System.currentTimeMillis();
    final var activated = createStartingZeebeRecord(time);
    var flowNodeInstanceEntity = fniTransformer.toFlowNodeInstanceEntity(activated, null);
    final var completed = createCompletedZeebeRecord(time);

    // when
    flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(completed, flowNodeInstanceEntity);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isEqualTo("1/3/4");
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(2);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(flowNodeInstanceEntity.getStartDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
    assertThat(flowNodeInstanceEntity.getEndDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
  }

  @Test
  public void shouldTransformTerminatedFNIRecord() {
    // given
    final var time = System.currentTimeMillis();
    final var record = createTerminatedZeebeRecord(time);

    // when
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        fniTransformer.toFlowNodeInstanceEntity(record, null);

    // then
    assertThat(flowNodeInstanceEntity).isNotNull();
    assertGeneralValues(flowNodeInstanceEntity);
    assertThat(flowNodeInstanceEntity.getTreePath()).isNull();
    assertThat(flowNodeInstanceEntity.getLevel()).isZero();
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getEndDate().toInstant())
        .isEqualTo(Instant.ofEpochMilli(time));
  }

  private static void assertGeneralValues(final FlowNodeInstanceEntity entity) {
    assertThat(entity.getBpmnProcessId()).isEqualTo("process");
    assertThat(entity.getFlowNodeId()).isEqualTo("element");
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(123);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(1);
    assertThat(entity.getKey()).isEqualTo(4L);
    assertThat(entity.getTenantId()).isEqualTo("none");
    assertThat(entity.getType()).isEqualTo(FlowNodeType.START_EVENT);
  }

  private static io.camunda.zeebe.protocol.record.Record createStartingZeebeRecord(
      final long timestamp) {
    return createZeebeRecord(timestamp, ProcessInstanceIntent.ELEMENT_ACTIVATING);
  }

  private static io.camunda.zeebe.protocol.record.Record createMigratedZeebeRecord(
      final long timestamp) {
    return createZeebeRecord(timestamp, ProcessInstanceIntent.ELEMENT_MIGRATED);
  }

  private static io.camunda.zeebe.protocol.record.Record createCompletedZeebeRecord(
      final long timestamp) {
    return createZeebeRecord(timestamp, ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  private static io.camunda.zeebe.protocol.record.Record createTerminatedZeebeRecord(
      final long timestamp) {
    return createZeebeRecord(timestamp, ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  protected static io.camunda.zeebe.protocol.record.Record createZeebeRecord(
      final long timestamp, final ProcessInstanceIntent intent) {
    return createZeebeRecord(timestamp, intent, BpmnElementType.START_EVENT);
  }

  protected static io.camunda.zeebe.protocol.record.Record createZeebeRecord(
      final long timestamp,
      final ProcessInstanceIntent intent,
      final BpmnElementType bpmnElementType) {
    final var recordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId("process")
            .withElementId("element")
            .withTenantId("none")
            .withProcessDefinitionKey(123)
            .withBpmnElementType(bpmnElementType)
            .withFlowScopeKey(3)
            .withProcessInstanceKey(1)
            .withVersion(12)
            .withBpmnEventType(BpmnEventType.NONE)
            .build();
    final var record = Mockito.mock(io.camunda.zeebe.protocol.record.Record.class);
    when(record.getKey()).thenReturn(4L);
    when(record.getValue()).thenReturn(recordValue);
    when(record.getIntent()).thenReturn(intent);
    when(record.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(record.getRecordType()).thenReturn(RecordType.EVENT);
    when(record.getTimestamp()).thenReturn(timestamp);
    return record;
  }
}
