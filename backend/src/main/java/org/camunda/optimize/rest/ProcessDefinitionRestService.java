package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.BranchAnalysisReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getRequestUser;


@Secured
@Path("/process-definition")
@Component
public class ProcessDefinitionRestService {

  @Autowired
  private BranchAnalysisReader branchAnalysisReader;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  /**
   * Retrieves all process definition stored in Optimize.
   *
   * @param includeXml A parameter saying if the process definition xml should be included to the response.
   * @return A collection of process definitions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Collection<ProcessDefinitionOptimizeDto> getProcessDefinitions(
      @Context ContainerRequestContext requestContext,
      @QueryParam("includeXml") boolean includeXml) {

    String userId = getRequestUser(requestContext);
    return processDefinitionReader.getProcessDefinitions(userId, includeXml);
  }

  /**
   * Retrieves all process definition stored in Optimize and groups them by key.
   *
   * @return A collection of process definitions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/groupedByKey")
  public List<ProcessDefinitionGroupOptimizeDto>
      getProcessDefinitionsGroupedByKey(@Context ContainerRequestContext requestContext) {
    String userId = getRequestUser(requestContext);
    return processDefinitionReader.getProcessDefinitionsGroupedByKey(userId);
  }

  /**
   * Get the process definition xml to a given process definition key and version.
   * If the version is set to "ALL", the xml of the latest version is returned.
   *
   * @param processDefinitionKey The process definition key of the desired process definition xml.
   * @param processDefinitionVersion The process definition version of the desired process definition xml.
   * @return The process definition xml requested.
   */
  @GET
  // xml on success, json on error
  @Produces(value = {MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @Path("/xml")
  public String getProcessDefinitionXml(
      @QueryParam("processDefinitionKey") String processDefinitionKey,
      @QueryParam("processDefinitionVersion") String processDefinitionVersion) {
    return processDefinitionReader.getProcessDefinitionXml(processDefinitionKey, processDefinitionVersion);
  }

  /**
   * Get the branch analysis from the given query information.
   *
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
