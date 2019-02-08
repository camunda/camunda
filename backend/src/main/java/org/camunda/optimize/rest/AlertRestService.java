package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.EmailAlertEnabledDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getRequestUserOrFailNotAuthorized;

@Path("/alert")
@Component
@Secured
public class AlertRestService {

  @Autowired
  private AlertService alertService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<AlertDefinitionDto> getStoredAlerts(@Context ContainerRequestContext requestContext) {
    String userId = getRequestUserOrFailNotAuthorized(requestContext);
    return alertService.getStoredAlerts(userId);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createAlert(
      @Context ContainerRequestContext requestContext,
      AlertCreationDto toCreate
  ) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    String user = AuthenticationUtil.getRequestUserOrFailNotAuthorized(requestContext);
    return alertService.createAlert(toCreate, user);
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateAlert(
      @Context ContainerRequestContext requestContext,
      @PathParam("id") String alertId,
      AlertCreationDto toCreate
  ) {
    ValidationHelper.ensureNotNull("creation object", toCreate);
    String user = AuthenticationUtil.getRequestUserOrFailNotAuthorized(requestContext);
    alertService.updateAlert(alertId, toCreate, user);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteAlert(@PathParam("id") String alertId) {
    alertService.deleteAlert(alertId);
  }

  @GET
  @Path("/email/isEnabled")
  @Produces(MediaType.APPLICATION_JSON)
  public EmailAlertEnabledDto isAlertingEnabled() {
    return alertService.isAlertingEnabled();
  }
}
