package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ACCESS_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_APPLICATIONS_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.GROUP_ENDPOINT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.MEMBER;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;

@Component
public class EngineAuthenticationProvider {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  private EngineContextFactory engineContextFactory;

  @Autowired
  private ConfigurationService configurationService;

  public boolean authenticate(CredentialsDto credentialsDto) {
    boolean result = false;
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      boolean isAuthenticated = performAuthenticationCheck(credentialsDto, engineContext);
      boolean isAuthorized = performAuthorizationCheck(credentialsDto, engineContext);
      if (isAuthenticated && isAuthorized) {
        result = true;
        break;
      }
    }
    return result;
  }

  private boolean performAuthorizationCheck(CredentialsDto credentialsDto, EngineContext engineContext) {
    List<GroupDto> groupsOfUser = getAllGroupsOfUser(credentialsDto.getUsername(), engineContext);
    List<AuthorizationDto> allAuthorizations = getAllAuthorizations(engineContext);
    List<AuthorizationDto> groupAuthorizations =
      extractGroupAuthorizations(groupsOfUser, allAuthorizations);
    List<AuthorizationDto> userAuthorizations =
      extractUserAuthorizations(credentialsDto, allAuthorizations);

    // NOTE: the order is essential here to make sure that
    // the revoking of permission works correctly
    boolean isAuthorized = checkIfGlobalUsageOfOptimizeIsGranted(allAuthorizations);
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

  private List<GroupDto> getAllGroupsOfUser(String username, EngineContext engineContext) {
    try {
      Response response = engineContext.getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()))
        .queryParam(MEMBER, username)
        .path(GROUP_ENDPOINT)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == 200) {
        return response.readEntity(new GenericType<List<GroupDto>>() {
        });
      }
    } catch (Exception e) {
      logger.error("Could not fetch groups for user [{}]", username, e);
    }
    return new ArrayList<>();
  }

  private List<AuthorizationDto> extractGroupAuthorizations(List<GroupDto> groupsOfUser,
                                                            List<AuthorizationDto> allAuthorizations) {
    Set<String> groupIds = groupsOfUser.stream().map(GroupDto::getId).collect(Collectors.toSet());
    return allAuthorizations
      .stream()
      .filter(a -> groupIds.contains(a.getGroupId()))
      .collect(Collectors.toList());
  }

  private List<AuthorizationDto> extractUserAuthorizations(CredentialsDto credentialsDto,
                                                           List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations
      .stream()
      .filter(a -> credentialsDto.getUsername().equals(a.getUserId()))
      .collect(Collectors.toList());
  }

  private boolean checkIfGlobalUsageOfOptimizeIsGranted(List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations.stream().anyMatch(this::grantsGloballyToUseOptimize);
  }

  private boolean isUserAuthorizationForAllResourcesGranted(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> grantsToUseOptimize(a, ALL_APPLICATIONS_RESOURCE_ID)
    );
  }

  private boolean isUserAuthorizationForOptimizeGranted(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> grantsToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID)
    );
  }

  private boolean isUserAuthorizationForAllResourcesRevoked(List<AuthorizationDto> userAuthorizations) {
    return userAuthorizations.stream().anyMatch(
      a -> revokesToUseOptimize(a, ALL_APPLICATIONS_RESOURCE_ID)
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
    return groupAuthorizations.stream().anyMatch(a -> grantsToUseOptimize(a, ALL_APPLICATIONS_RESOURCE_ID));
  }

  private boolean doesAnyGroupRevokeAuthorizationForOptimize(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> revokesToUseOptimize(a, OPTIMIZE_APPLICATION_RESOURCE_ID));
  }

  private boolean doesAnyGroupRevokeAuthorizationForAllResources(List<AuthorizationDto> groupAuthorizations) {
    return groupAuthorizations.stream().anyMatch(a -> revokesToUseOptimize(a, ALL_APPLICATIONS_RESOURCE_ID));
  }

  private boolean grantsToUseOptimize(AuthorizationDto a, String resource) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean applicationResourceType = a.getResourceType() == 0;
    boolean optimizeResourceId = a.getResourceId().toLowerCase().trim().equals(resource);
    return hasPermissions && grantPermission && applicationResourceType && optimizeResourceId;
  }

  private boolean revokesToUseOptimize(AuthorizationDto a, String resource) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean applicationResourceType = a.getResourceType() == RESOURCE_TYPE_APPLICATION;
    boolean optimizeResourceId = a.getResourceId().toLowerCase().trim().equals(resource);
    return hasPermissions && grantPermission && applicationResourceType && optimizeResourceId;
  }

  private boolean grantsGloballyToUseOptimize(AuthorizationDto a) {
    boolean hasPermissions =
      a.getPermissions().stream()
        .anyMatch(p -> p.equals(ALL_PERMISSION) || p.equals(ACCESS_PERMISSION));
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GLOBAL;
    boolean applicationResourceType = a.getResourceType() == RESOURCE_TYPE_APPLICATION;
    boolean optimizeResourceId =
      a.getResourceId().toLowerCase().equals(OPTIMIZE_APPLICATION_RESOURCE_ID) ||
        a.getResourceId().trim().equals(ALL_APPLICATIONS_RESOURCE_ID);
    return hasPermissions && grantPermission && applicationResourceType && optimizeResourceId;
  }

  private List<AuthorizationDto> getAllAuthorizations(EngineContext engineContext) {
    try {
      Response response = engineContext.getEngineClient()
        .target(configurationService.getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()))
        .path(AUTHORIZATION_ENDPOINT)
        .request(MediaType.APPLICATION_JSON)
        .get();
      if (response.getStatus() == 200) {
        return response.readEntity(new GenericType<List<AuthorizationDto>>() {});
      }
    } catch (Exception e) {
      logger.error("Could not fetch authorizations from the Engine to check the access permissions.", e);
    }
    return new ArrayList<>();
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
