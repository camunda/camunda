package org.camunda.operate.zeebe;

import java.util.HashSet;
import java.util.Set;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.es.writer.EntityStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.api.events.IncidentEvent;
import io.zeebe.client.api.events.IncidentState;
import io.zeebe.client.api.subscription.IncidentEventHandler;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Profile("zeebe")
public class IncidentEventTransformer extends AbstractEventTransformer implements IncidentEventHandler {

  private Logger logger = LoggerFactory.getLogger(WorkflowInstanceEventTransformer.class);

  private final static Set<IncidentState> EVENTS = new HashSet<>();

  static {
    EVENTS.add(IncidentState.CREATED);
    EVENTS.add(IncidentState.RESOLVED);
  }

  @Autowired
  private EntityStorage entityStorage;

  @Override
  public void onIncidentEvent(IncidentEvent event) throws Exception {
    if (EVENTS.contains(event.getState())) {

      logger.debug(event.toJson());

      IncidentEntity incidentEntity = new IncidentEntity();

      incidentEntity.setId(String.valueOf(event.getMetadata().getKey()));
      incidentEntity.setErrorType(event.getErrorType());
      incidentEntity.setErrorMessage(event.getErrorMessage());
      incidentEntity.setActivityId(event.getActivityId());
      if (event.getActivityInstanceKey() != null) {
        incidentEntity.setActivityInstanceId(String.valueOf(event.getActivityInstanceKey()));
      }
      if (event.getJobKey() != null) {
        incidentEntity.setTaskId(String.valueOf(event.getJobKey()));
      }
      if (event.getWorkflowInstanceKey() != null) {
        incidentEntity.setWorkflowInstanceId(String.valueOf(event.getWorkflowInstanceKey()));
      }
//      incidentEntity.setProcessDefinitionKey(event.getBpmnProcessId());

      if (IncidentState.RESOLVED.equals(event.getState())){
        incidentEntity.setState(org.camunda.operate.entities.IncidentState.RESOLVED);
      }
      else {
        incidentEntity.setState(org.camunda.operate.entities.IncidentState.ACTIVE);
      }

      updateMetdataFields(incidentEntity, event);

      //TODO will wait till capacity available, can throw InterruptedException
      entityStorage.getOperateEntititesQueue(event.getMetadata().getTopicName()).put(incidentEntity);
    }
  }
}
