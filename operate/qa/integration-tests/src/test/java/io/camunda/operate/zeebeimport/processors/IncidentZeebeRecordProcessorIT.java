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

import static io.camunda.operate.util.TestUtil.createIncident;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.MIGRATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord.Builder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class IncidentZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  private final int newVersion = 111;
  private final String newBpmnProcessId = "newBpmnProcessId";
  private final long newProcessDefinitionKey = 111;
  private final String newFlowNodeId = "newFlowNodeId";
  @Autowired private IncidentTemplate incidentTemplate;
  @Autowired private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
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
  public void shouldOverrideIncidentFields() throws IOException, PersistenceException {
    // having
    // incident entity with position = 1
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE); // null positions field
    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentTemplate.getFullQualifiedName(), inc.getId(), inc);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(MIGRATED),
            b ->
                b.withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey)
                    .withElementId(newFlowNodeId));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final IncidentEntity updatedInc = findIncidentByKey(inc.getKey());
    // old values
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    assertThat(updatedInc.getKey()).isEqualTo(inc.getKey());
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    // new values
    assertThat(updatedInc.getBpmnProcessId()).isEqualTo(newBpmnProcessId);
    assertThat(updatedInc.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedInc.getFlowNodeId()).isEqualTo(newFlowNodeId);
    assertThat(updatedInc.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideIncidentFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // incident entity with position = 1
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE).setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentTemplate.getFullQualifiedName(), inc.getId(), inc);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(MIGRATED),
            b ->
                b.withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey)
                    .withElementId(newFlowNodeId));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final IncidentEntity updatedInc = findIncidentByKey(inc.getKey());
    // old values
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    assertThat(updatedInc.getKey()).isEqualTo(inc.getKey());
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    // new values
    assertThat(updatedInc.getBpmnProcessId()).isEqualTo(newBpmnProcessId);
    assertThat(updatedInc.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedInc.getFlowNodeId()).isEqualTo(newFlowNodeId);
    assertThat(updatedInc.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideIncidentFields() throws IOException, PersistenceException {
    // having
    // incident entity with position = 2
    final long oldPosition = 2L;
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE).setPosition(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentTemplate.getFullQualifiedName(), inc.getId(), inc);

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(MIGRATED),
            b ->
                b.withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey)
                    .withElementId(newFlowNodeId));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final IncidentEntity updatedInc = findIncidentByKey(inc.getKey());
    // old values
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    assertThat(updatedInc.getKey()).isEqualTo(inc.getKey());
    assertThat(updatedInc.getTenantId()).isEqualTo(inc.getTenantId());
    // old values
    assertThat(updatedInc.getBpmnProcessId()).isEqualTo(inc.getBpmnProcessId());
    assertThat(updatedInc.getProcessDefinitionKey()).isEqualTo(inc.getProcessDefinitionKey());
    assertThat(updatedInc.getFlowNodeId()).isEqualTo(inc.getFlowNodeId());
    assertThat(updatedInc.getPosition()).isEqualTo(oldPosition);
  }

  @NotNull
  private IncidentEntity findIncidentByKey(final long key) throws IOException {
    final List<IncidentEntity> entities =
        testSearchRepository.searchTerm(
            incidentTemplate.getFullQualifiedName(), "key", key, IncidentEntity.class, 10);
    final Optional<IncidentEntity> first =
        entities.stream().filter(p -> p.getKey() == key).findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private void importIncidentZeebeRecord(final Record<IncidentRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    incidentZeebeRecordProcessor.processIncidentRecord(List.of(zeebeRecord), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(incidentTemplate.getFullQualifiedName());
  }

  @NotNull
  private static Record<IncidentRecordValue> createZeebeRecordFromIncident(
      final IncidentEntity inc,
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableIncidentRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<IncidentRecordValue> builder = ImmutableRecord.builder();
    final ImmutableIncidentRecordValue.Builder valueBuilder =
        ImmutableIncidentRecordValue.builder();
    valueBuilder
        .withBpmnProcessId(inc.getBpmnProcessId())
        .withElementInstanceKey(inc.getFlowNodeInstanceKey())
        .withErrorMessage(inc.getErrorMessage())
        .withElementId(inc.getFlowNodeId())
        .withBpmnProcessId(inc.getBpmnProcessId())
        .withErrorType(ErrorType.valueOf(inc.getErrorType().name()))
        .withTenantId(inc.getTenantId());
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withKey(inc.getKey())
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build());
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }
}
