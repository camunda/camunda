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
package io.camunda.tasklist.zeebeimport.v840.processors.os;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.zeebeimport.common.ProcessDefinitionDeletionProcessor;
import io.camunda.tasklist.zeebeimport.util.XMLUtil;
import io.camunda.tasklist.zeebeimport.v840.record.value.deployment.DeployedProcessImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessZeebeRecordProcessorOpenSearch.class);

  private static final Set<String> STATES_TO_PERSIST = Set.of(ProcessIntent.CREATED.name());

  private static final Set<String> STATES_TO_DELETE = Set.of(ProcessIntent.DELETED.name());

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired private XMLUtil xmlUtil;

  @Autowired private ProcessDefinitionDeletionProcessor processDefinitionDeletionProcessor;

  public void processDeploymentRecord(
      Record<DeployedProcessImpl> record, List<BulkOperation> operations)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final String processDefinitionKey = String.valueOf(record.getValue().getProcessDefinitionKey());

    if (STATES_TO_PERSIST.contains(intentStr)) {
      final DeployedProcessImpl recordValue = record.getValue();

      final Map<String, String> userTaskForms = new HashMap<>();
      persistProcess(recordValue, operations, userTaskForms::put);
      final List<PersistenceException> exceptions = new ArrayList<>();
      userTaskForms.forEach(
          (formKey, schema) -> {
            try {
              persistForm(
                  processDefinitionKey, formKey, schema, recordValue.getTenantId(), operations);
            } catch (PersistenceException e) {
              exceptions.add(e);
            }
          });
      if (!exceptions.isEmpty()) {
        throw exceptions.get(0);
      }
    } else if (STATES_TO_DELETE.contains(intentStr)) {
      operations.addAll(
          processDefinitionDeletionProcessor.createProcessDefinitionDeleteRequests(
              processDefinitionKey,
              (index, id) ->
                  new BulkOperation.Builder()
                      .delete(DeleteOperation.of(del -> del.index(index).id(id)))
                      .build()));
    }
  }

  private void persistProcess(
      Process process,
      List<BulkOperation> operations,
      BiConsumer<String, String> userTaskFormCollector) {

    final ProcessEntity processEntity = createEntity(process, userTaskFormCollector);
    LOGGER.debug("Process: key {}", processEntity.getKey());

    operations.add(
        new BulkOperation.Builder()
            .index(
                IndexOperation.of(
                    io ->
                        io.index(processIndex.getFullQualifiedName())
                            .id(ConversionUtils.toStringOrNull(processEntity.getKey()))
                            .document(CommonUtils.getJsonObjectFromEntity(processEntity))))
            .build());
  }

  private ProcessEntity createEntity(
      Process process, BiConsumer<String, String> userTaskFormCollector) {
    final ProcessEntity processEntity =
        new ProcessEntity()
            .setId(String.valueOf(process.getProcessDefinitionKey()))
            .setKey(process.getProcessDefinitionKey())
            .setBpmnProcessId(process.getBpmnProcessId())
            .setVersion(process.getVersion())
            .setTenantId(process.getTenantId());

    final byte[] byteArray = process.getResource();

    xmlUtil.extractDiagramData(
        byteArray,
        process.getBpmnProcessId()::equals,
        processEntity::setName,
        flowNode -> processEntity.getFlowNodes().add(flowNode),
        userTaskFormCollector,
        processEntity::setFormKey,
        formId -> processEntity.setFormId(formId),
        processEntity::setStartedByForm);

    Optional.ofNullable(processEntity.getFormKey())
        .ifPresent(key -> processEntity.setIsFormEmbedded(true));

    Optional.ofNullable(processEntity.getFormId())
        .ifPresent(
            id -> {
              processEntity.setIsFormEmbedded(false);
            });

    return processEntity;
  }

  private void persistForm(
      String processDefinitionKey,
      String formKey,
      String schema,
      String tenantId,
      List<BulkOperation> operations)
      throws PersistenceException {
    final FormEntity formEntity = new FormEntity(processDefinitionKey, formKey, schema, tenantId);
    LOGGER.debug("Form: key {}", formKey);

    operations.add(
        new BulkOperation.Builder()
            .index(
                IndexOperation.of(
                    io ->
                        io.index(formIndex.getFullQualifiedName())
                            .id(ConversionUtils.toStringOrNull(formEntity.getId()))
                            .document(CommonUtils.getJsonObjectFromEntity(formEntity))))
            .build());
  }
}
