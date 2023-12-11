package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.zeebeimport.util.XMLUtil;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.indices.ProcessIndex;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;

public class ProcessHandler implements ExportHandler<ProcessEntity, Process> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessHandler.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private static final Set<Intent> STATES = new HashSet<>();
  static {
    STATES.add(ProcessIntent.CREATED);
  }

  private ProcessIndex processIndex;

  private XMLUtil xmlUtil = new XMLUtil();

  public ProcessHandler(ProcessIndex processIndex) {
    this.processIndex = processIndex;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS;
  }

  @Override
  public Class<ProcessEntity> getEntityType() {
    return ProcessEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<Process> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public String generateId(Record<Process> record) {
    return String.valueOf(record.getValue().getProcessDefinitionKey());
  }

  @Override
  public ProcessEntity createNewEntity(String id) {
    return new ProcessEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<Process> record, ProcessEntity entity) {
    final Process process = record.getValue();

    entity.setKey(process.getProcessDefinitionKey()).setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion()).setTenantId(tenantOrDefault(process.getTenantId()));

    final byte[] byteArray = process.getResource();

    final String bpmn = new String(byteArray, CHARSET);
    entity.setBpmnXml(bpmn);

    final String resourceName = process.getResourceName();
    entity.setResourceName(resourceName);

    final Optional<ProcessEntity> diagramData = xmlUtil.extractDiagramData(byteArray);
    if (diagramData.isPresent()) {
      entity.setName(diagramData.get().getName()).setFlowNodes(diagramData.get().getFlowNodes());
    }
  }

  @Override
  public void flush(ProcessEntity processEntity, BatchRequest batchRequest)
      throws PersistenceException {
    LOGGER.debug("Process: key {}, bpmnProcessId {}", processEntity.getKey(),
        processEntity.getBpmnProcessId());

    // TODO: afaik this code updates the version in process instance records, if they have been
    // imported
    // before the process itself was seen. This race condition should not exist anymore

    // List<Long> processInstanceKeys =
    // listViewStore.getProcessInstanceKeysWithEmptyProcessVersionFor(processEntity.getKey());
    // for (Long processInstanceKey : processInstanceKeys) {
    // Map<String, Object> updateFields = new HashMap<>();
    // updateFields.put(ListViewTemplate.PROCESS_NAME, processEntity.getName());
    // updateFields.put(ListViewTemplate.PROCESS_VERSION, processEntity.getVersion());
    // batchRequest.update(listViewTemplate.getFullQualifiedName(), processInstanceKey.toString(),
    // updateFields);
    // }

    batchRequest.addWithId(processIndex.getFullQualifiedName(),
        ConversionUtils.toStringOrNull(processEntity.getKey()), processEntity);

  }

  @Override
  public String getIndexName() {
    return processIndex.getFullQualifiedName();
  }

}
