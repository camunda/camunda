package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.BranchAnalysisReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getRequestUser;

@Secured
@Component
@Path("/analysis")
public class AnalysisRestService {
  @Autowired
  private BranchAnalysisReader branchAnalysisReader;

  /**
   * Get the branch analysis from the given query information.
   *
   * @return All information concerning the branch analysis.
   */
  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public BranchAnalysisDto getBranchAnalysis(@Context ContainerRequestContext requestContext,
                                             BranchAnalysisQueryDto branchAnalysisDto) {
    String userId = getRequestUser(requestContext);
    return branchAnalysisReader.branchAnalysis(userId, branchAnalysisDto);
  }
}
