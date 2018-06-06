package org.camunda.operate.rest;

import java.util.Random;
import org.camunda.operate.rest.dto.CountResultDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;

/**
 * @author Svetlana Dorokhova.
 */
@RestController
@RequestMapping(value = WORKFLOW_INSTANCE_URL)
public class WorkflowInstanceRestService {

  public static final String WORKFLOW_INSTANCE_URL = "/workflow-instance";

  @PostMapping("/count")
  public CountResultDto queryWorkflowInstancesCount(@RequestBody WorkflowInstanceQueryDto workflowInstanceQuery) {
    //return mocks
    Random random = new Random();
    if (workflowInstanceQuery.isWithIncidents() || workflowInstanceQuery.isWithoutIncidents()) {
      return new CountResultDto(random.nextInt(100000) + 400000);
    } else if (workflowInstanceQuery.isRunning() && workflowInstanceQuery.isCompleted()){
      return new CountResultDto(0);
    } else {
      return new CountResultDto(random.nextInt(200000) + 800000);
    }
  }

}
