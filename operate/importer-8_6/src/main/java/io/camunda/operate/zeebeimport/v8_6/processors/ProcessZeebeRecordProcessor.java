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
package io.camunda.operate.zeebeimport.v8_6.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessZeebeRecordProcessor.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private static final Set<String> STATES = new HashSet<>();

  static {
    STATES.add(ProcessIntent.CREATED.name());
  }

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private ListViewStore listViewStore;

  @Autowired private ProcessIndex processIndex;

  @Autowired private XMLUtil xmlUtil;

  public void processDeploymentRecord(final Record record, final BatchRequest batchRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();

    if (STATES.contains(intentStr)) {
      final ProcessMetadataValue recordValue = (ProcessMetadataValue) record.getValue();
      persistProcess((Process) recordValue, batchRequest);
    }
  }

  private void persistProcess(final Process process, final BatchRequest batchRequest)
      throws PersistenceException {
    final ProcessEntity processEntity = createEntity(process);
    LOGGER.debug(
        "Process: key {}, bpmnProcessId {}",
        processEntity.getKey(),
        processEntity.getBpmnProcessId());
    updateFieldsInInstancesFor(processEntity, batchRequest);
    batchRequest.addWithId(
        processIndex.getFullQualifiedName(),
        ConversionUtils.toStringOrNull(processEntity.getKey()),
        processEntity);
  }

  private void updateFieldsInInstancesFor(
      final ProcessEntity processEntity, final BatchRequest batchRequest)
      throws PersistenceException {
    final List<Long> processInstanceKeys =
        listViewStore.getProcessInstanceKeysWithEmptyProcessVersionFor(processEntity.getKey());
    for (final Long processInstanceKey : processInstanceKeys) {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.PROCESS_NAME, processEntity.getName());
      updateFields.put(ListViewTemplate.PROCESS_VERSION, processEntity.getVersion());
      batchRequest.update(
          listViewTemplate.getFullQualifiedName(), processInstanceKey.toString(), updateFields);
    }
  }

  private ProcessEntity createEntity(final Process process) {
    final ProcessEntity processEntity =
        new ProcessEntity()
            .setId(String.valueOf(process.getProcessDefinitionKey()))
            .setKey(process.getProcessDefinitionKey())
            .setBpmnProcessId(process.getBpmnProcessId())
            .setVersion(process.getVersion())
            .setTenantId(tenantOrDefault(process.getTenantId()));

    final byte[] byteArray = process.getResource();

    final String bpmn = new String(byteArray, CHARSET);
    processEntity.setBpmnXml(bpmn);

    final String resourceName = process.getResourceName();
    processEntity.setResourceName(resourceName);

    final Optional<ProcessEntity> diagramData =
        xmlUtil.extractDiagramData(byteArray, process.getBpmnProcessId());
    if (diagramData.isPresent()) {
      processEntity
          .setName(diagramData.get().getName())
          .setFlowNodes(diagramData.get().getFlowNodes());
    }
    return processEntity;
  }
}
