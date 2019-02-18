package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.service.es.schema.type.UserTaskInstanceType;

public class MedianUserTaskIdleDurationByUserTaskCommand extends MedianUserTaskDurationByUserTaskCommand {
  @Override
  protected String getDurationFieldName() {
    return UserTaskInstanceType.IDLE_DURATION;
  }
}
