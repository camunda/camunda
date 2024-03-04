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
package io.camunda.operate.webapp.zeebe.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.zeebe.client.api.command.MigrationPlanImpl;
import java.util.ArrayList;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Operation handler to migrate process instances */
@Component
public class MigrateProcessInstanceHandler extends AbstractOperationHandler
    implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(MigrateProcessInstanceHandler.class);

  @Autowired private OperationsManager operationsManager;

  @Autowired private ProcessReader processReader;

  @Autowired private ProcessStore processStore;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public void handleWithException(OperationEntity operation) throws Exception {

    Long processInstanceKey = operation.getProcessInstanceKey();
    if (processInstanceKey == null) {
      failOperation(operation, "No process instance key is provided.");
      return;
    }

    MigrationPlanDto migrationPlanDto =
        objectMapper.readValue(operation.getMigrationPlan(), MigrationPlanDto.class);
    long targetProcessDefinitionKey =
        Long.parseLong(migrationPlanDto.getTargetProcessDefinitionKey());

    MigrationPlan migrationPlan =
        new MigrationPlanImpl(targetProcessDefinitionKey, new ArrayList<>());
    migrationPlanDto
        .getMappingInstructions()
        .forEach(
            mapping ->
                migrationPlan
                    .getMappingInstructions()
                    .add(
                        new MigrationPlanBuilderImpl.MappingInstruction(
                            mapping.getSourceElementId(), mapping.getTargetElementId())));

    logger.info(
        String.format(
            "Operation [%s]: Sending Zeebe migrate command for processInstanceKey [%s]...",
            operation.getId(), processInstanceKey));
    zeebeClient
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(migrationPlan)
        .send()
        .join();
    markAsSent(operation);
    logger.info(
        String.format(
            "Operation [%s]: Migrate command sent to Zeebe for processInstanceKey [%s]",
            operation.getId(), processInstanceKey));
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.MIGRATE_PROCESS_INSTANCE);
  }
}
