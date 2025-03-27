/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.writer;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
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

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchProcessInstanceWriter.class);

  @Autowired private ListViewTemplate processInstanceTemplate;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private ProcessStore processStore;

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

  @Override
  public void deleteInstanceById(final Long id) throws IOException {
    final ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(id);
    validateDeletion(processInstanceEntity);
    deleteProcessInstanceAndDependants(processInstanceEntity.getProcessInstanceKey().toString());
  }

  private void deleteProcessInstanceAndDependants(final String processInstanceKey)
      throws IOException {
    final List<ProcessInstanceDependant> processInstanceDependantsWithoutOperation =
        processInstanceDependantTemplates.stream()
            .filter(t -> !(t instanceof OperationTemplate))
            .toList();
    for (final ProcessInstanceDependant template : processInstanceDependantsWithoutOperation) {
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

  private void deleteProcessInstanceFromTreePath(final String processInstanceKey) {
    processStore.deleteProcessInstanceFromTreePath(processInstanceKey);
  }

  private long deleteDocument(final String indexName, final String idField, final String id)
      throws IOException {
    return processStore.deleteDocument(indexName, idField, id);
  }
}
