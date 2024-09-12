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
package io.camunda.operate.zeebeimport.v8_4.processors.fni;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.zeebeimport.cache.FNITreePathCacheCompositeKey;
import io.camunda.operate.zeebeimport.cache.TreePathCache;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.util.Set;

/**
 * Transformer to transform a given Zeebe flow node instance record to a {@link
 * FlowNodeInstanceEntity}.
 */
public class FNITransformer {

  private static final Set<String> FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());
  private static final Set<String> START_STATES = Set.of(ELEMENT_ACTIVATING.name());

  private static final Set<BpmnElementType> CONTAINER_TYPES =
      Set.of(
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.MULTI_INSTANCE_BODY,
          BpmnElementType.PROCESS);
  private final TreePathCache treePathCache;

  public FNITransformer(final TreePathCache treePathCache) {
    this.treePathCache = treePathCache;
  }

  private static FNITreePathCacheCompositeKey toCompositeKey(
      final Record<?> record, final ProcessInstanceRecordValue recordValue) {
    return new FNITreePathCacheCompositeKey(
        record.getPartitionId(),
        record.getKey(),
        recordValue.getFlowScopeKey(),
        recordValue.getProcessInstanceKey());
  }

  /**
   * Transform the given Zeebe flow node instance record into a {@link FlowNodeInstanceEntity}.
   *
   * <p>If the given entity is not-null the entity will be updated/extended.
   *
   * @param record the Zeebe flow node instance record
   * @param entity the entity which should be updated, if null a new entity is created
   * @return the newly created or updated entity
   */
  public FlowNodeInstanceEntity toFlowNodeInstanceEntity(
      final Record<ProcessInstanceRecordValue> record, FlowNodeInstanceEntity entity) {
    if (entity == null) {
      entity = new FlowNodeInstanceEntity();
    }

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setFlowNodeId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (FINISH_STATES.contains(intentStr)) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setState(FlowNodeState.TERMINATED);
      } else {
        entity.setState(FlowNodeState.COMPLETED);
      }
      entity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else {
      entity.setState(FlowNodeState.ACTIVE);
      if (START_STATES.contains(intentStr)) {
        entity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
        entity.setPosition(record.getPosition());
      }

      // We resolve the treePath only when necessary, for example when not done earlier
      // and when in an active state, for completed and terminated we expect the treePath
      // already be resolved before so we skip this to reduce the load on our cache / backend
      // storage
      if (entity.getTreePath() == null) {
        final FNITreePathCacheCompositeKey compositeKey = toCompositeKey(record, recordValue);
        final String parentTreePath = treePathCache.resolveParentTreePath(compositeKey);

        final String treePath =
            String.join("/", parentTreePath, ConversionUtils.toStringOrNull(record.getKey()));
        entity.setTreePath(treePath);
        entity.setLevel(parentTreePath.split("/").length);

        // We cache only treePath's of container types to reduce way too many evictions
        // when storing leafs, which are never requested
        if (CONTAINER_TYPES.contains(recordValue.getBpmnElementType())) {
          treePathCache.cacheTreePath(compositeKey, treePath);
        }
      }
    }

    entity.setType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));

    return entity;
  }
}
