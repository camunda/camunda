/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.writer;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchProcessInstanceWriter
    implements io.camunda.operate.webapp.writer.ProcessInstanceWriter {

  private static final Logger logger =
      LoggerFactory.getLogger(OpensearchProcessInstanceWriter.class);

  @Autowired private ListViewTemplate processInstanceTemplate;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private ProcessStore processStore;

  @Override
  public void deleteInstanceById(Long id) throws IOException {
    ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(id);
    validateDeletion(processInstanceEntity);
    deleteProcessInstanceAndDependants(processInstanceEntity.getProcessInstanceKey().toString());
  }

  private static void validateDeletion(
      final ProcessInstanceForListViewEntity processInstanceEntity) {
    if (!STATES_FOR_DELETION.contains(processInstanceEntity.getState())) {
      throw new IllegalArgumentException(
          String.format(
              "Process instances needs to be in one of the states %s", STATES_FOR_DELETION));
    }
    if (processInstanceEntity.getEndDate() == null
        || processInstanceEntity.getEndDate().isAfter(OffsetDateTime.now())) {
      throw new IllegalArgumentException(
          String.format(
              "Process instances needs to have an endDate before now: %s < %s",
              processInstanceEntity.getEndDate(), OffsetDateTime.now()));
    }
  }

  private void deleteProcessInstanceAndDependants(final String processInstanceKey)
      throws IOException {
    List<ProcessInstanceDependant> processInstanceDependantsWithoutOperation =
        processInstanceDependantTemplates.stream()
            .filter(t -> !(t instanceof OperationTemplate))
            .toList();
    for (ProcessInstanceDependant template : processInstanceDependantsWithoutOperation) {
      deleteDocument(
          template.getFullQualifiedName() + "*",
          ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
          processInstanceKey);
    }
    deleteProcessInstanceFromTreePath(processInstanceKey);
    deleteDocument(
        processInstanceTemplate.getIndexPattern(),
        ListViewTemplate.PROCESS_INSTANCE_KEY,
        processInstanceKey);
  }

  private void deleteProcessInstanceFromTreePath(String processInstanceKey) {
    processStore.deleteProcessInstanceFromTreePath(processInstanceKey);
  }

  private long deleteDocument(final String indexName, final String idField, String id)
      throws IOException {
    return processStore.deleteDocument(indexName, idField, id);
  }
}
