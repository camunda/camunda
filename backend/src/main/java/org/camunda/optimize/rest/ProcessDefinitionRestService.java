package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.HeatMapResponseDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.BranchAnalysisReader;
import org.camunda.optimize.service.es.reader.DurationHeatMapReader;
import org.camunda.optimize.service.es.reader.FrequencyHeatMapReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
  private BranchAnalysisReader branchAnalysisReader;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  /**
   * Retrieves all process definition stored in Optimize.
   * @param includeXml A parameter saying if the process definition xml should be included to the response.
   * @return A collection of process definitions.
   * @throws OptimizeException Something went wrong while fetching the process definitions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Collection<ExtendedProcessDefinitionOptimizeDto> getProcessDefinitions(
      @QueryParam("includeXml") boolean includeXml) throws OptimizeException {
    return processDefinitionReader.getProcessDefinitions(includeXml);
  }

  /**
   * Get the process definition xml to a given process definition id.
   * @param processDefinitionId The process definition id the xml should be returned.
   * @return The process definition xml requested.
   */
  @GET
  @Path("/{id}/xml")
  public String getProcessDefinitionXml(@PathParam("id") String processDefinitionId) {
    return processDefinitionReader.getProcessDefinitionXml(processDefinitionId);
  }

  /**
   * Get the frequency heat map of a certain process definition.
   * @param processDefinitionId Telling of which process definition the heat map is requested.
   * @return the frequency heat map.
   */
  @GET
  @Path("/{id}/heatmap/frequency")
  @Produces(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getFrequencyHeatMap(@PathParam("id") String processDefinitionId) {
    return frequencyHeatMapReader.getHeatMap(processDefinitionId);
  }

  /**
   * Get the frequency heat map of a certain process definition.
   * @return the frequency heat map.
   */
  @POST
  @Path("/heatmap/frequency")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getFrequencyHeatMap(HeatMapQueryDto to) {
    return frequencyHeatMapReader.getHeatMap(to);
  }

  /**
   * Get the duration heat map of a certain process definition.
   * @param processDefinitionId Telling of which process definition the heat map is requested.
   * @return the duration heat map.
   */
  @GET
  @Path("/{id}/heatmap/duration")
  @Produces(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getDurationHeatMap(@PathParam("id") String processDefinitionId) {
    return durationHeatMapReader.getHeatMap(processDefinitionId);
  }

  /**
   * Get the duration heat map of a certain process definition.
   * @return the duration heat map.
   */
  @POST
  @Path("/heatmap/duration")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public HeatMapResponseDto getDurationHeatMap(HeatMapQueryDto to) {
    return durationHeatMapReader.getHeatMap(to);
  }

  /**
   * Get the branch analysis from the given query information.
   * @return All information concerning the branch analysis.
   */
  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public BranchAnalysisDto getBranchAnalysis(BranchAnalysisQueryDto to) {
    return branchAnalysisReader.branchAnalysis(to);
  }


}
