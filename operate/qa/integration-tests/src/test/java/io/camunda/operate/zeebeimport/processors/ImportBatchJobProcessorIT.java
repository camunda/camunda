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

import static io.camunda.operate.entities.listview.ProcessInstanceState.COMPLETED;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.ImportBatchProcessor;
import io.camunda.operate.zeebeimport.ImportBatchProcessorFactory;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.processors.ImportBatchFactory.ImportBatchBuilder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ImportBatchJobProcessorIT extends OperateSearchAbstractIT {

  // TODO: do something about this hard coded version?
  private static final String PROCESS_UNDER_TEST = "8.5";

  // TODO: make this constants
  final int newVersion = 111;
  final String newBpmnProcessId = "newBpmnProcessId";
  final long newProcessDefinitionKey = 111;
  final String newProcessName = "New process name";

  @Autowired private ImportBatchProcessorFactory importBatchProcessorFactory;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ListViewTemplate listViewTemplate;

  @MockBean private ProcessCache processCache;
  @MockBean private PartitionHolder partitionHolder;

  @Autowired private ImportPositionHolder importPositionHolder;
  private boolean concurrencyModeBefore;

  @Autowired private SchemaManager schemaManager;
  @Autowired private OperateProperties properties;

  private ImportBatchFactory importBatchFactory;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final int partitionid = 1;

    when(partitionHolder.getPartitionIds()).thenReturn(List.of(partitionid));
    concurrencyModeBefore = importPositionHolder.getConcurrencyMode();
    importPositionHolder.setConcurrencyMode(true);

    importBatchFactory = new ImportBatchFactory(partitionid, objectMapper);
  }

  @Override
  @AfterAll
  public void afterAllTeardown() {
    importPositionHolder.setConcurrencyMode(concurrencyModeBefore);
    super.afterAllTeardown();
  }

  @Test
  public void shouldOverrideFieldsForOlderProcessInstanceRecords()
      throws PersistenceException, IOException {

    // given
    // records as JSON (instance of ImportBatch)
    when(processCache.getProcessNameOrDefaultValue(eq(newProcessDefinitionKey), anyString()))
        .thenReturn(newProcessName);
    final ImportBatchProcessor batchProcessor =
        importBatchProcessorFactory.getImportBatchProcessor(PROCESS_UNDER_TEST);

    final ProcessInstanceForListViewEntity pi = createProcessInstance().setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), pi.getId(), pi);

    final long newPosition = 2L;

    final ImportBatchBuilder importBatchBuilder =
        importBatchFactory.newBatch(ValueType.PROCESS_INSTANCE, ImportValueType.PROCESS_INSTANCE);
    importBatchBuilder.withRecord(
        b ->
            b.withPosition(newPosition)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withKey(pi.getKey())
                .withValue(
                    ImmutableProcessInstanceRecordValue.builder()
                        .withVersion(newVersion)
                        .withProcessInstanceKey(pi.getProcessInstanceKey())
                        .withBpmnProcessId(newBpmnProcessId)
                        .withBpmnElementType(BpmnElementType.PROCESS)
                        .withProcessDefinitionKey(newProcessDefinitionKey)
                        .withTenantId(pi.getTenantId())
                        .build())
                .withPartitionId(1)
                .withTimestamp(Instant.now().toEpochMilli()));

    final ImportBatch importBatch = importBatchBuilder.build();

    // when
    batchProcessor.performImport(importBatch);
    refreshAllIndexes();

    // then
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

  private void refreshAllIndexes() {
    schemaManager.refresh(properties.getIndexPrefix() + "*");
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
}
