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

import static io.camunda.operate.util.TestUtil.createVariable;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createZeebeRecordFromVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class VariableZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  @Autowired private VariableTemplate variableTemplate;
  @Autowired private VariableZeebeRecordProcessor variableZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
  @Autowired private ImportPositionHolder importPositionHolder;
  private final String newVarValue = "newVarValue";

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1));
  }

  @Test
  public void shouldOverrideVariableFields() throws IOException, PersistenceException {
    // having
    // variable entity with position = 1
    final VariableEntity var = createVariable(111L, 222L, "varName", "varValue").setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        variableTemplate.getFullQualifiedName(), var.getId(), var);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<VariableRecordValue> zeebeRecord =
        createZeebeRecordFromVariable(
            var,
            b -> b.withPosition(newPosition).withIntent(VariableIntent.UPDATED),
            b -> b.withValue(newVarValue));
    importVariableZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final VariableEntity updatedVar = findVariableById(var.getId());
    // old values
    assertThat(updatedVar.getTenantId()).isEqualTo(var.getTenantId());
    assertThat(updatedVar.getName()).isEqualTo(var.getName());
    // new values
    assertThat(updatedVar.getValue()).isEqualTo(newVarValue);
    assertThat(updatedVar.getFullValue()).isEqualTo(null);
    assertThat(updatedVar.getIsPreview()).isFalse();
    assertThat(updatedVar.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideVariableFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // variable entity with empty position
    final VariableEntity var = createVariable(111L, 222L, "varName", "varValue"); // null position
    testSearchRepository.createOrUpdateDocumentFromObject(
        variableTemplate.getFullQualifiedName(), var.getId(), var);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<VariableRecordValue> zeebeRecord =
        createZeebeRecordFromVariable(
            var,
            b -> b.withPosition(newPosition).withIntent(VariableIntent.UPDATED),
            b -> b.withValue(newVarValue));
    importVariableZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final VariableEntity updatedVar = findVariableById(var.getId());
    // old values
    assertThat(updatedVar.getTenantId()).isEqualTo(var.getTenantId());
    assertThat(updatedVar.getName()).isEqualTo(var.getName());
    // new values
    assertThat(updatedVar.getValue()).isEqualTo(newVarValue);
    assertThat(updatedVar.getFullValue()).isEqualTo(null);
    assertThat(updatedVar.getIsPreview()).isFalse();
    assertThat(updatedVar.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideVariableFields() throws IOException, PersistenceException {
    // having
    // variable entity with position = 2L
    final long oldPosition = 2L;
    final VariableEntity var =
        createVariable(111L, 222L, "varName", "varValue").setPosition(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        variableTemplate.getFullQualifiedName(), var.getId(), var);

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final Record<VariableRecordValue> zeebeRecord =
        createZeebeRecordFromVariable(
            var,
            b -> b.withPosition(newPosition).withIntent(VariableIntent.MIGRATED),
            b -> b.withValue(newVarValue));
    importVariableZeebeRecord(zeebeRecord);

    // then
    // process instance fields are updated
    final VariableEntity updatedVar = findVariableById(var.getId());
    // old values
    assertThat(updatedVar.getTenantId()).isEqualTo(var.getTenantId());
    assertThat(updatedVar.getName()).isEqualTo(var.getName());
    // old values
    assertThat(updatedVar.getValue()).isEqualTo(var.getValue());
    assertThat(updatedVar.getFullValue()).isEqualTo(var.getFullValue());
    assertThat(updatedVar.getIsPreview()).isEqualTo(var.getIsPreview());
    assertThat(updatedVar.getPosition()).isEqualTo(oldPosition);
  }

  @Test
  public void shouldNotClearVariableValueDuringMigration()
      throws PersistenceException, IOException {
    // given
    final VariableEntity var = createVariable(111L, 222L, "varName", "varValue");
    testSearchRepository.createOrUpdateDocumentFromObject(
        variableTemplate.getFullQualifiedName(), var.getId(), var);

    // when
    // importing MIGRATED Zeebe record
    final Record<VariableRecordValue> zeebeRecord =
        createZeebeRecordFromVariable(
            var,
            b -> b.withPosition(1L).withIntent(VariableIntent.MIGRATED),
            b -> b.withValue(null));
    importVariableZeebeRecord(zeebeRecord);

    // then
    // the variable value has not been set to null but is still the old value
    final VariableEntity updatedVar = findVariableById(var.getId());
    assertThat(updatedVar.getValue()).isEqualTo(var.getValue());
  }

  @NotNull
  private VariableEntity findVariableById(final String id) throws IOException {
    final List<VariableEntity> entities =
        testSearchRepository.searchTerm(
            variableTemplate.getFullQualifiedName(), "_id", id, VariableEntity.class, 10);
    final Optional<VariableEntity> first = entities.stream().findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private void importVariableZeebeRecord(final Record<VariableRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    variableZeebeRecordProcessor.processVariableRecords(
        Map.of(zeebeRecord.getValue().getScopeKey(), List.of(zeebeRecord)), batchRequest, true);
    batchRequest.execute();
    searchContainerManager.refreshIndices(variableTemplate.getFullQualifiedName());
  }
}
