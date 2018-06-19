package org.camunda.operate.util;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.writer.ElasticsearchBulkProcessor;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@ConditionalOnProperty(name= OperateProperties.PREFIX + ".elasticsearch.demoData", havingValue="true")
@Profile("elasticsearch")
@DependsOn("elasticsearchSchemaManager")
public class EsDemoDataGenerator {

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @PostConstruct
  public void generateESData() {

    Random random = new Random();

    final long count = workflowInstanceReader.countWorkflowInstances(new WorkflowInstanceQueryDto());
    if (count == 0) {

      List<OperateEntity> workflowInstances = new ArrayList<>();
      for (int i = 0; i < random.nextInt(100) + 500; i++) {

        WorkflowInstanceEntity workflowInstance = new WorkflowInstanceEntity();
        workflowInstance.setId(UUID.randomUUID().toString());
        workflowInstance.setBusinessKey("process" + random.nextInt(10));
        workflowInstance.setStartDate(DateUtil.getRandomStartDate());
        final OffsetDateTime endDate = DateUtil.getRandomEndDate(true);
        workflowInstance.setEndDate(endDate);
        workflowInstance.setState(endDate == null ? WorkflowInstanceState.ACTIVE : WorkflowInstanceState.COMPLETED);


        //create active incidents
        if (endDate == null) {
          if (random.nextInt(10) % 3 == 0) {
            workflowInstance.getIncidents().add(createIncident(IncidentState.ACTIVE));
          }
        }

        //create resolved incidents
        if (random.nextInt(10) % 5 == 0) {
          workflowInstance.getIncidents().add(createIncident(IncidentState.RESOLVED));
        }

        workflowInstance.setWorkflowId(UUID.randomUUID().toString());
        workflowInstances.add(workflowInstance);
      }

      elasticsearchBulkProcessor.persistOperateEntities(workflowInstances);
    }

  }

  private IncidentEntity createIncident(IncidentState state) {
    IncidentEntity incidentEntity = new IncidentEntity();
    incidentEntity.setId(UUID.randomUUID().toString());
    incidentEntity.setActivityId("start");
    incidentEntity.setActivityInstanceId(UUID.randomUUID().toString());
    incidentEntity.setErrorType("TASK_NO_RETRIES");
    incidentEntity.setErrorMessage("No more retries left.");
    incidentEntity.setState(state);
    return incidentEntity;
  }

}
