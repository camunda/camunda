package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.HeatMapRequestDto;
import org.camunda.optimize.service.es.HeatMapReader;
import org.camunda.optimize.service.es.ProcessDefinitionReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/process-definition")
@Component
public class ProcessDefinition {

  @Autowired
  private HeatMapReader heatMapReader;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessDefinitionDto> getProcessDefinitions() {
    return processDefinitionReader.getProcessDefinitions();
  }

  @GET
  @Path("/{id}/xml")
  public String getProcessDefinitionXml(@PathParam("id") String processDefinitionId) {
    return processDefinitionReader.getProcessDefinitionXmls(processDefinitionId);
  }

  @GET
  @Path("/{id}/heatmap")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, Long> getHeatMap(@PathParam("id") String processDefinitionId) {
    return heatMapReader.getHeatMap(processDefinitionId);
  }

  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Long getCorrelation(HeatMapRequestDto to) {
    return heatMapReader.activityCorrelation(to.getProcessDefinitionId(), to.getCorrelationActivities());
  }


}
