package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.CorrelationQueryDto;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.GatewaySplitDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.CorrelationReader;
import org.camunda.optimize.service.es.reader.DurationHeatMapReader;
import org.camunda.optimize.service.es.reader.FrequencyHeatMapReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

@Secured
@Path("/process-definition")
@Component
public class ProcessDefinitionRestService {

  @Autowired
  private FrequencyHeatMapReader frequencyHeatMapReader;

  @Autowired
  private DurationHeatMapReader durationHeatMapReader;

  @Autowired
  private CorrelationReader correlationReader;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Collection<ExtendedProcessDefinitionOptimizeDto> getProcessDefinitions(
      @QueryParam("includeXml") boolean includeXml) throws OptimizeException {
    return processDefinitionReader.getProcessDefinitions(includeXml);
  }

  @GET
  @Path("/{id}/xml")
  public String getProcessDefinitionXml(@PathParam("id") String processDefinitionId) {
    return processDefinitionReader.getProcessDefinitionXml(processDefinitionId);
  }

  @GET
  @Path("/{id}/heatmap/frequency")
  @Produces(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getFrequencyHeatMap(@PathParam("id") String processDefinitionId) {
    return frequencyHeatMapReader.getHeatMap(processDefinitionId);
  }

  @POST
  @Path("/heatmap/frequency")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getFrequencyHeatMap(HeatMapQueryDto to) {
    return frequencyHeatMapReader.getHeatMap(to);
  }

  @GET
  @Path("/{id}/heatmap/duration")
  @Produces(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getDurationHeatMap(@PathParam("id") String processDefinitionId) {
    return durationHeatMapReader.getHeatMap(processDefinitionId);
  }

  @POST
  @Path("/heatmap/duration")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getDurationHeatMap(HeatMapQueryDto to) {
    return durationHeatMapReader.getHeatMap(to);
  }

  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public GatewaySplitDto getCorrelation(CorrelationQueryDto to) {
    return correlationReader.activityCorrelation(to);
  }


}
