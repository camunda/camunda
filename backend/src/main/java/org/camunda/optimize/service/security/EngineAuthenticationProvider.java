package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ACCESS_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;

@Component
public class EngineAuthenticationProvider {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private ConfigurationService configurationService;

  public boolean authenticate(CredentialsDto credentialsDto, EngineContext engineContext) {
    boolean result = false;
      boolean isAuthenticated = performAuthenticationCheck(credentialsDto, engineContext);
      if (isAuthenticated) {
        boolean isAuthorized = performAuthorizationCheck(credentialsDto.getUsername(), engineContext);
        if (isAuthorized) {
          result = true;
        }
      }
    return result;
  }

  private boolean performAuthorizationCheck(String username, EngineContext engineContext) {
    List<GroupDto> groupsOfUser = engineContext.getAllGroupsOfUser(username);
    List<AuthorizationDto> allApplicationAuthorizations = engineContext.getAllApplicationAuthorizations();
    List<AuthorizationDto> groupAuthorizations =
      extractGroupAuthorizations(groupsOfUser, allApplicationAuthorizations);
    List<AuthorizationDto> userAuthorizations =
      extractUserAuthorizations(username, allApplicationAuthorizations);

    // NOTE: the order is essential here to make sure that
    // the revoking of permission works correctly
    boolean isAuthorized = checkIfGlobalUsageOfOptimizeIsGranted(allApplicationAuthorizations);
    isAuthorized &= !doesAnyGroupRevokeAuthorizationForAllResources(groupAuthorizations);
    isAuthorized |= doesAnyGroupGrantAuthorizationForOptimize(groupAuthorizations);
    isAuthorized &= !doesAnyGroupRevokeAuthorizationForOptimize(groupAuthorizations);
    isAuthorized |= doesAnyGroupGrantAuthorizationForAllResources(groupAuthorizations);
    isAuthorized &= !isUserAuthorizationForAllResourcesRevoked(userAuthorizations);
    isAuthorized |= isUserAuthorizationForAllResourcesGranted(userAuthorizations);
    isAuthorized &= !isUserAuthorizationForOptimizeRevoked(userAuthorizations);
    isAuthorized |= isUserAuthorizationForOptimizeGranted(userAuthorizations);

    return isAuthorized;
  }

  private List<AuthorizationDto> extractGroupAuthorizations(List<GroupDto> groupsOfUser,
                                                            List<AuthorizationDto> allAuthorizations) {
    Set<String> groupIds = groupsOfUser.stream().map(GroupDto::getId).collect(Collectors.toSet());
    return allAuthorizations
      .stream()
      .filter(a -> groupIds.contains(a.getGroupId()))
      .collect(Collectors.toList());
  }

  private List<AuthorizationDto> extractUserAuthorizations(String username,
                                                           List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations
      .stream()
      .filter(a -> username.equals(a.getUserId()))
      .collect(Collectors.toList());
  }

  private boolean checkIfGlobalUsageOfOptimizeIsGranted(List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations.stream().anyMatch(this::grantsGloballyToUseOptimize);
  }

  private boolean isUserAuthorizationForAllResourcesGranted(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> grantsToUseOptimize(a, ALL_RESOURCES_RESOURCE_ID)
    );
  }

  private boolean isUserAuthorizationForOptimizeGranted(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> grantsToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID)
    );
  }

  private boolean isUserAuthorizationForAllResourcesRevoked(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> revokesToUseOptimize(a, ALL_RESOURCES_RESOURCE_ID)
    );
  }

  private boolean isUserAuthorizationForOptimizeRevoked(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> revokesToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID)
    );
  }

  private boolean doesAnyGroupGrantAuthorizationForOptimize(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> grantsToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID));
  }

  private boolean doesAnyGroupGrantAuthorizationForAllResources(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> grantsToUseOptimize(a, ALL_RESOURCES_RESOURCE_ID));
  }

  private boolean doesAnyGroupRevokeAuthorizationForOptimize(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> revokesToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID));
  }

  private boolean doesAnyGroupRevokeAuthorizationForAllResources(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> revokesToUseOptimize(a, ALL_RESOURCES_RESOURCE_ID));
  }

  private boolean grantsToUseOptimize(AuthorizationDto a, String resource) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean optimizeResourceId = a.getResourceId().toLowerCase().trim().equals(resource);
    return hasPermissions && grantPermission && optimizeResourceId;
  }

  private boolean revokesToUseOptimize(AuthorizationDto a, String resource) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean optimizeResourceId = a.getResourceId().toLowerCase().trim().equals(resource);
    return hasPermissions && grantPermission && optimizeResourceId;
  }

  private boolean grantsGloballyToUseOptimize(AuthorizationDto a) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GLOBAL;
    boolean optimizeResourceId =
      a.getResourceId().toLowerCase().equals(OPTIMIZE_APPLICATION_RESOURCE_ID) ||
        a.getResourceId().trim().equals(ALL_RESOURCES_RESOURCE_ID);
    return hasPermissions && grantPermission && optimizeResourceId;
  }




  private boolean performAuthenticationCheck(CredentialsDto credentialsDto, EngineContext engineContext) {
    boolean authenticated = false;
    try {
      Response response = engineContext.getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()))
        .path(configurationService.getUserValidationEndpoint())
        .request(MediaType.APPLICATION_JSON)
        .post(Entity.json(credentialsDto));

      if (response.getStatus() == 200) {
        AuthenticationResultDto responseEntity = response.readEntity(AuthenticationResultDto.class);
        authenticated = responseEntity.isAuthenticated();
      } else {
        logger.error("Could not validate user [{}] against the engine [{}]. " +
            "Maybe you did not provide a user or password.",
          credentialsDto.getUsername(),
          engineContext.getEngineAlias()
        );
      }

    } catch (Exception e) {
      logger.error("Could not validate user [{}] against the engine [{}]. Please check the connection to the engine!",
        credentialsDto.getUsername(),
        engineContext.getEngineAlias(),
        e);
    }

    return authenticated;
  }

}
