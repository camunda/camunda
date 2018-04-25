package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.user.OptimizeUserDto;
import org.camunda.optimize.dto.optimize.query.user.PermissionsDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.TokenService;
import org.camunda.optimize.service.user.UserService;
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
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.List;


@Secured
@Path("/user")
@Component
public class UserRestService {

  @Autowired
  private UserService userService;

  @Autowired
  private TokenService tokenService;


  /**
   * Create a new user in Optimize
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void createNewUser(@Context ContainerRequestContext requestContext,
                               CredentialsDto userPassword) {
    String token = AuthenticationUtil.getToken(requestContext);
    String creatorId = tokenService.getTokenIssuer(token);
    userService.createNewUser(userPassword, creatorId);
  }

  /**
   * Update the permissions for a given user.
   */
  @PUT
  @Path("/{id}/permission")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateUserPermission(@Context ContainerRequestContext requestContext,
                                 @PathParam("id") String userId,
                                 PermissionsDto updatedPermissions) {
    String token = AuthenticationUtil.getToken(requestContext);
    String modifierId = tokenService.getTokenIssuer(token);
    userService.updatePermission(userId, updatedPermissions, modifierId);
  }


  /**
   * Update the password for a given user.
   */
  @PUT
  @Path("/{id}/password")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.TEXT_PLAIN)
  public void updateUserPassword(@Context ContainerRequestContext requestContext,
                                 @PathParam("id") String userId,
                                 String password) {
    String token = AuthenticationUtil.getToken(requestContext);
    String modifierId = tokenService.getTokenIssuer(token);
    userService.updatePassword(userId, password, modifierId);
  }

  /**
   * Retrieve all users stored in Optimize.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<OptimizeUserDto> getStoredReports(@Context UriInfo uriInfo) throws IOException {
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
    return userService.findAllUsers(queryParameters);
  }

  /**
   * Retrieve the user specified by id.
   */
  @GET
  @Path("/withId/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public OptimizeUserDto getUserById(@PathParam("id") String userId) {
    return userService.getUser(userId);
  }

  /**
   * Retrieve the user information for the user that is performing the request.
   */
  @GET
  @Path("/currentUser")
  @Produces(MediaType.APPLICATION_JSON)
  public OptimizeUserDto getCurrentUser(@Context ContainerRequestContext requestContext) {
    String token = AuthenticationUtil.getToken(requestContext);
    String userId = tokenService.getTokenIssuer(token);
    return userService.getUser(userId);
  }

  /**
   * Delete the user to the specified id.
   */
  @DELETE
  @Path("/withId/{id}")
  public void deleteUser(@PathParam("id") String userId) {
    userService.deleteUser(userId);
  }


}
