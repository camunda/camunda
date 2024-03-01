/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
  public void deleteInstanceById(Long id) throws IOException {
    ProcessInstanceForListViewEntity processInstanceEntity =
        processInstanceReader.getProcessInstanceByKey(id);
    validateDeletion(processInstanceEntity);
    deleteProcessInstanceAndDependants(processInstanceEntity.getProcessInstanceKey().toString());
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
