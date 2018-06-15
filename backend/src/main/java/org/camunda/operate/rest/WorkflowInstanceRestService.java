package org.camunda.operate.rest;

import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;

import java.util.List;

import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.rest.dto.CountResultDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Svetlana Dorokhova.
 */
@RestController
@RequestMapping(value = WORKFLOW_INSTANCE_URL)
@Profile("elasticsearch")
public class WorkflowInstanceRestService {

  public static final String WORKFLOW_INSTANCE_URL = "/api/workflow-instances";

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @PostMapping("/count")
  public CountResultDto queryWorkflowInstancesCount(@RequestBody WorkflowInstanceQueryDto workflowInstanceQuery) {
    final long count = workflowInstanceReader.countWorkflowInstances(workflowInstanceQuery);
    return new CountResultDto(count);
  }

  @PostMapping
  public List<WorkflowInstanceDto> queryWorkflowInstances(
      @RequestBody WorkflowInstanceQueryDto workflowInstanceQuery,
      @RequestParam("firstResult") Integer firstResult,
      @RequestParam("maxResults") Integer maxResults) {
    final List<WorkflowInstanceEntity> workflowInstanceEntities = workflowInstanceReader.queryWorkflowInstances(workflowInstanceQuery, firstResult, maxResults);
    return WorkflowInstanceDto.createFrom(workflowInstanceEntities);
  }

  @GetMapping("/{id}")
  public WorkflowInstanceDto queryWorkflowInstanceById(@PathVariable String id) {
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(id);
    return WorkflowInstanceDto.createFrom(workflowInstanceEntity);
  }

}
