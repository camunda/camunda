package org.camunda.bpm.platform.plugin.duration.adaption;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.history.event.HistoricActivityInstanceEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoryEvent;
import org.camunda.bpm.engine.impl.history.producer.CacheAwareHistoryEventProducer;

/**
 * Ensures that all activities with the name "aSimpleServiceTask" have
 * the duration set which is given in the variable "activityDuration".
 */
public class DurationAdaptionHistoryEventProducer extends CacheAwareHistoryEventProducer {

  @Override
  public HistoryEvent createActivityInstanceEndEvt(DelegateExecution execution) {
    HistoricActivityInstanceEventEntity event =
      (HistoricActivityInstanceEventEntity) super.createActivityInstanceEndEvt(execution);
    if(event.getActivityId().equals("aSimpleServiceTask")) {
      Long duration = (Long) execution.getVariable("activityDuration");
      if (duration != null) {
        event.setDurationInMillis(duration);
      }
    } else if (event.getActivityId().equals("aSimpleServiceTask2")) {
      Long duration = (Long) execution.getVariable("activityDuration2");
      if (duration != null) {
        event.setDurationInMillis(duration);
      }
    }
    return event;
  }
}
