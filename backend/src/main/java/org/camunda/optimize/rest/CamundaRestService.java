package org.camunda.optimize.rest;

import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/camunda")
@Secured
@Component
public class CamundaRestService {
  @Autowired
  private ConfigurationService configurationService;

  /**
   * Provides endpoint link to the Camunda Webapplications if enabled.
   *
   * @return Response code 200 (OK) and
   * -> Response code 200 (OK): the Camunda Webapplications (Cockpit, Admin, Tasklist) endpoint as
   * plain test, e.g. http://localhost:8080/camunda, if the feature is enabled
   * -> esponse code 200 (No-content) if the feature is disabled
   */
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public Response getCamundaWebappsEndpoint() {
    if (configurationService.getCamundaWebappsEndpointEnabled()) {
      return Response.ok(configurationService.getCamundaWebappsEndpoint()).build();
    } else {
      return Response.noContent().build();
    }
  }
}