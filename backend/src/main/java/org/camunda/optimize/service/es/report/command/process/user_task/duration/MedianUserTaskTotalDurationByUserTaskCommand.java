package org.camunda.optimize.service.es.report.command.process.user_task.duration;

import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;

public class MedianUserTaskTotalDurationByUserTaskCommand extends MedianUserTaskDurationByUserTaskCommand {
  @Override
  protected String getDurationFieldName() {
    return ProcessInstanceType.USER_TASK_TOTAL_DURATION;
  }
}
