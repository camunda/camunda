package org.camunda.operate.rest;

import java.util.Collection;
import java.util.List;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;

import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.rest.dto.ActivityStatisticsDto;
import org.camunda.operate.rest.dto.WorkflowInstanceBatchOperationDto;
import org.camunda.operate.rest.dto.WorkflowInstanceDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

@Api(tags = {"Workflow instances"})
@SwaggerDefinition(tags = {
  @Tag(name = "Workflow instances", description = "Workflow instances")
})
@RestController
@RequestMapping(value = WORKFLOW_INSTANCE_URL)
public class WorkflowInstanceRestService {

  public static final String WORKFLOW_INSTANCE_URL = "/api/workflow-instances";

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private ListViewReader listViewReader;

  @ApiOperation("Query workflow instances by different parameters")
  @PostMapping
  public ListViewResponseDto queryWorkflowInstances(
      @RequestBody ListViewRequestDto workflowInstanceRequest,
      @RequestParam("firstResult") Integer firstResult,   //required
      @RequestParam("maxResults") Integer maxResults) {   //required
    for (ListViewQueryDto query : workflowInstanceRequest.getQueries()) {
      if (query.getWorkflowVersion() != null && query.getBpmnProcessId() == null) {
        throw new InvalidRequestException("BpmnProcessId must be provided in request, when workflow version is not null.");
      }
    }
    return listViewReader.queryWorkflowInstances(workflowInstanceRequest, firstResult, maxResults);
  }

  @ApiOperation("Perform batch operation on selection (async)")
  @PostMapping("/operation")
  public void batchOperation(
      @RequestBody WorkflowInstanceBatchOperationDto batchOperationRequest) throws PersistenceException {
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  @ApiOperation("Get workflow instance by id")
  @GetMapping("/{id}")
  public WorkflowInstanceDto queryWorkflowInstanceById(@PathVariable String id) {
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(id);
    return WorkflowInstanceDto.createFrom(workflowInstanceEntity);
  }

  @ApiOperation("Get activity instance statistics")
  @PostMapping(path = "/statistics")
  public Collection<ActivityStatisticsDto> getStatistics(@RequestBody ListViewRequestDto workflowInstanceRequest) {
    final List<ListViewQueryDto> queries = workflowInstanceRequest.getQueries();
    if (queries.size() != 1) {
      throw new InvalidRequestException("Exactly one query must be specified in the request.");
    }
    final List<String> workflowIds = queries.get(0).getWorkflowIds();
    final String bpmnProcessId = queries.get(0).getBpmnProcessId();
    final Integer workflowVersion = queries.get(0).getWorkflowVersion();

    if ( (workflowIds != null && workflowIds.size() == 1) == (bpmnProcessId != null && workflowVersion != null) ) {
      throw new InvalidRequestException("Exactly one workflow must be specified in the request (via workflowIds or bpmnProcessId/version).");
    }
    return listViewReader.getActivityStatistics(queries.get(0));
  }

}
