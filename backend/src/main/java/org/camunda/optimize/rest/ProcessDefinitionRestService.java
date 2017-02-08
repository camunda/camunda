package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.service.es.reader.HeatMapReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/process-definition")
@Component
public class ProcessDefinitionRestService {

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
  @Path("/heatmap")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Map<String, Long> getHeatMap(HeatMapQueryDto to) {
    return heatMapReader.getHeatMap(to);
  }

  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Long getCorrelation(HeatMapQueryDto to) {
    return heatMapReader.activityCorrelation(to.getProcessDefinitionId(), to.getFlowNodes());
  }


}
