package org.camunda.operate.rest;

import org.camunda.operate.es.reader.DetailViewReader;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeDto;
import org.camunda.operate.rest.dto.detailview.ActivityInstanceTreeRequestDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static org.camunda.operate.rest.ActivityInstanceRestService.ACTIVITY_INSTANCE_URL;

@Api(tags = {"Activity instances"})
@SwaggerDefinition(tags = {
  @Tag(name = "Activity instances", description = "Activity instances")
})
@RestController
@RequestMapping(value = ACTIVITY_INSTANCE_URL)
public class ActivityInstanceRestService {

  public static final String ACTIVITY_INSTANCE_URL = "/api/activity-instances";

  @Autowired
  private DetailViewReader detailViewReader;

  @ApiOperation("Query activity instance tree")
  @PostMapping
  public ActivityInstanceTreeDto queryActivityInstanceTree(
      @RequestBody ActivityInstanceTreeRequestDto request) {
    if (request == null || request.getWorkflowInstanceId() == null) {
      throw new InvalidRequestException("Workflow instance id must be provided when requesting for activity instance tree.");
    }
    return detailViewReader.getActivityInstanceTree(request);
  }

}
