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
package io.camunda.tasklist.zeebeimport.v840.processors.es;

import static io.camunda.tasklist.util.ConversionUtils.toStringOrNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
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
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessZeebeRecordProcessorElasticSearch.class);

  private static final Set<String> STATES_TO_PERSIST = Set.of(ProcessIntent.CREATED.name());
  private static final Set<String> STATES_TO_DELETE = Set.of(ProcessIntent.DELETED.name());

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ProcessIndex processIndex;

  @Autowired private FormIndex formIndex;

  @Autowired private XMLUtil xmlUtil;

  @Autowired private ProcessDefinitionDeletionProcessor processDefinitionDeletionProcessor;

  public void processDeploymentRecord(Record<DeployedProcessImpl> record, BulkRequest bulkRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final DeployedProcessImpl recordValue = record.getValue();
    final String processDefinitionKey = String.valueOf(record.getValue().getProcessDefinitionKey());
    if (STATES_TO_PERSIST.contains(intentStr)) {
      final Map<String, String> userTaskForms = new HashMap<>();
      persistProcess(recordValue, bulkRequest, userTaskForms::put);

      final List<PersistenceException> exceptions = new ArrayList<>();
      userTaskForms.forEach(
          (formKey, schema) -> {
            try {
              persistForm(
                  processDefinitionKey, formKey, schema, bulkRequest, recordValue.getTenantId());
            } catch (PersistenceException e) {
              exceptions.add(e);
            }
          });
      if (!exceptions.isEmpty()) {
        throw exceptions.get(0);
      }
    } else if (STATES_TO_DELETE.contains(intentStr)) {
      bulkRequest.add(
          processDefinitionDeletionProcessor.createProcessDefinitionDeleteRequests(
              processDefinitionKey, DeleteRequest::new));
    }
  }

  private void persistProcess(
      Process process, BulkRequest bulkRequest, BiConsumer<String, String> userTaskFormCollector)
      throws PersistenceException {

    final ProcessEntity processEntity = createEntity(process, userTaskFormCollector);
    LOGGER.debug("Process: key {}", processEntity.getKey());

    try {
      bulkRequest.add(
          new IndexRequest()
              .index(processIndex.getFullQualifiedName())
              .id(toStringOrNull(processEntity.getKey()))
              .source(objectMapper.writeValueAsString(processEntity), XContentType.JSON));
    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert process [%s]", processEntity.getKey()),
          e);
    }
  }

  private ProcessEntity createEntity(
      Process process, BiConsumer<String, String> userTaskFormCollector) {
    final ProcessEntity processEntity = new ProcessEntity();

    processEntity.setId(String.valueOf(process.getProcessDefinitionKey()));
    processEntity.setKey(process.getProcessDefinitionKey());
    processEntity.setBpmnProcessId(process.getBpmnProcessId());
    processEntity.setVersion(process.getVersion());
    processEntity.setTenantId(process.getTenantId());

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
      BulkRequest bulkRequest,
      String tenantId)
      throws PersistenceException {
    final FormEntity formEntity = new FormEntity(processDefinitionKey, formKey, schema, tenantId);
    LOGGER.debug("Form: key {}", formKey);
    try {
      bulkRequest.add(
          new IndexRequest()
              .index(formIndex.getFullQualifiedName())
              .id(toStringOrNull(formEntity.getId()))
              .source(objectMapper.writeValueAsString(formEntity), XContentType.JSON));

    } catch (JsonProcessingException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert task form [%s]", formEntity.getId()),
          e);
    }
  }
}
