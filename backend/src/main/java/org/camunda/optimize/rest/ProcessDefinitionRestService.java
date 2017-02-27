package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.CorrelationQueryDto;
import org.camunda.optimize.dto.optimize.GatewaySplitDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.CorrelationReader;
import org.camunda.optimize.service.es.reader.HeatMapReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/process-definition")
@Component
public class ProcessDefinitionRestService {

  @Autowired
  private HeatMapReader heatMapReader;

  @Autowired
  private CorrelationReader correlationReader;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ProcessDefinitionOptimizeDto> getProcessDefinitions() {
    return processDefinitionReader.getProcessDefinitions();
  }

  @GET
  @Path("/{id}/xml")
  public String getProcessDefinitionXml(@PathParam("id") String processDefinitionId) {
    return processDefinitionReader.getProcessDefinitionXml(processDefinitionId);
  }

  @GET
  @Path("/{id}/heatmap")
  @Produces(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getHeatMap(@PathParam("id") String processDefinitionId) {
    return heatMapReader.getHeatMap(processDefinitionId);
  }

  @POST
  @Path("/heatmap")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getHeatMap(HeatMapQueryDto to) {
    return heatMapReader.getHeatMap(to);
  }

  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public GatewaySplitDto getCorrelation(CorrelationQueryDto to) {
    return correlationReader.activityCorrelation(to);
  }


}
