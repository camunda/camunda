package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.service.es.schema.type.UserTaskInstanceType;

public class AverageUserTaskTotalDurationByUserTaskCommand extends AverageUserTaskDurationByUserTaskCommand {
  @Override
  protected String getDurationFieldName() {
    return UserTaskInstanceType.TOTAL_DURATION;
  }
}
