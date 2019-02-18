package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.service.es.schema.type.UserTaskInstanceType;

public abstract class UserTaskGroupingCommand extends FlowNodeGroupingCommand {

  @Override
  protected String getProcessDefinitionKeyPropertyName() {
    return UserTaskInstanceType.PROCESS_DEFINITION_KEY;
  }

  @Override
  protected String getProcessDefinitionVersionPropertyName() {
    return UserTaskInstanceType.PROCESS_DEFINITION_VERSION;
  }
}
