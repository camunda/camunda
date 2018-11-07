package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.engine.AuthorizationDto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GLOBAL;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_REVOKE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;

public class Session {

  private TokenVerifier tokenVerifier;
  private org.camunda.optimize.service.security.DefinitionAuthorizations definitionAuthorizations;

  Session(TokenVerifier tokenVerifier, org.camunda.optimize.service.security.DefinitionAuthorizations definitionAuthorizations) {
    this.tokenVerifier = tokenVerifier;
    this.definitionAuthorizations = definitionAuthorizations;
  }

  public void updateDefinitionAuthorizations(org.camunda.optimize.service.security.DefinitionAuthorizations definitionAuthorizations) {
    this.definitionAuthorizations = definitionAuthorizations;
  }

  public void updateExpiryDate() {
    tokenVerifier.updateExpiryDate();
  }

  public boolean isTokenValid(String token) {
    return tokenVerifier.isTokenValid(token);
  }

  public boolean hasTokenExpired(String token) {
    return tokenVerifier.hasExpired(token);
  }

  public boolean isAuthorizedToSeeDefinition(String processDefinitionKey) {

    if (processDefinitionKey == null || processDefinitionKey.isEmpty()) {
      return true;
    }

    // NOTE: the order is essential here to make sure that
    // the revoking of definition permissions works correctly
    DefinitionAuthorizations definitionDefinitionAuthorizations = new DefinitionAuthorizations();
    addGloballyAuthorizedDefinitions(this.definitionAuthorizations.getAllDefinitionAuthorizations(), definitionDefinitionAuthorizations);
    // group authorizations
    removeAuthorizationsForAllDefinitions(this.definitionAuthorizations.getGroupAuthorizations(), definitionDefinitionAuthorizations);
    addAuthorizationsForAllDefinitions(this.definitionAuthorizations.getGroupAuthorizations(), definitionDefinitionAuthorizations);
    removeAuthorizationsForProhibitedDefinition(this.definitionAuthorizations.getGroupAuthorizations(), definitionDefinitionAuthorizations);
    addAuthorizationsForSingleDefinitions(this.definitionAuthorizations.getGroupAuthorizations(), definitionDefinitionAuthorizations);
    // user authorizations
    removeAuthorizationsForAllDefinitions(this.definitionAuthorizations.getUserAuthorizations(), definitionDefinitionAuthorizations);
    addAuthorizationsForAllDefinitions(this.definitionAuthorizations.getUserAuthorizations(), definitionDefinitionAuthorizations);
    removeAuthorizationsForProhibitedDefinition(this.definitionAuthorizations.getUserAuthorizations(), definitionDefinitionAuthorizations);
    addAuthorizationsForSingleDefinitions(this.definitionAuthorizations.getUserAuthorizations(), definitionDefinitionAuthorizations);

    return definitionDefinitionAuthorizations.isAuthorizedToSeeDefinition(processDefinitionKey);
  }

  private void addGloballyAuthorizedDefinitions(List<AuthorizationDto> authorizations,
                                                DefinitionAuthorizations definitionDefinitionAuthorizations) {
    authorizations.forEach(a -> addGloballyAuthorizedDefinition(a, definitionDefinitionAuthorizations));
  }

  private void addAuthorizationsForAllDefinitions(List<AuthorizationDto> authorizations,
                                                  DefinitionAuthorizations definitionDefinitionAuthorizations) {
    authorizations.forEach(a -> addAuthorizationForAllDefinitions(a, definitionDefinitionAuthorizations));
  }

  private void addAuthorizationsForSingleDefinitions(List<AuthorizationDto> authorizations,
                                                     DefinitionAuthorizations definitionDefinitionAuthorizations) {
    authorizations.forEach(a -> addAuthorizationForDefinition(a, definitionDefinitionAuthorizations));
  }

  private void removeAuthorizationsForAllDefinitions(List<AuthorizationDto> authorizations,
                                                     DefinitionAuthorizations definitionDefinitionAuthorizations) {
    authorizations.forEach(a -> removeAuthorizationForAllDefinitions(a, definitionDefinitionAuthorizations));
  }

  private void removeAuthorizationsForProhibitedDefinition(List<AuthorizationDto> authorizations,
                                                           DefinitionAuthorizations definitionDefinitionAuthorizations) {
    authorizations.forEach(a -> removeAuthorizationForProhibitedDefinition(a, definitionDefinitionAuthorizations));
  }

  private void addGloballyAuthorizedDefinition(AuthorizationDto a,
                                               DefinitionAuthorizations definitionDefinitionAuthorizations) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean globalGrantPermission = a.getType() == AUTHORIZATION_TYPE_GLOBAL;
    boolean processDefinitionResourceType = a.getResourceType() == RESOURCE_TYPE_PROCESS_DEFINITION;
    if (hasPermissions && globalGrantPermission && processDefinitionResourceType) {
      String resourceId = a.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        definitionDefinitionAuthorizations.grantToSeeAllDefinitions();
      } else if (!resourceId.isEmpty()) {
        definitionDefinitionAuthorizations.authorizeDefinition(resourceId);
      }
    }
  }

  private boolean hasCorrectPermissions(AuthorizationDto a) {
    List<String> permissions = a.getPermissions();
    return permissions.contains(ALL_PERMISSION) || permissions.contains(READ_HISTORY_PERMISSION);
  }

  private void addAuthorizationForAllDefinitions(AuthorizationDto a,
                                                 DefinitionAuthorizations definitionDefinitionAuthorizations) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean processDefinitionResourceType = a.getResourceType() == RESOURCE_TYPE_PROCESS_DEFINITION;
    if (hasPermissions && grantPermission && processDefinitionResourceType) {
      String resourceId = a.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        definitionDefinitionAuthorizations.grantToSeeAllDefinitions();
      }
    }
  }

  private void addAuthorizationForDefinition(AuthorizationDto a,
                                             DefinitionAuthorizations definitionDefinitionAuthorizations) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean processDefinitionResourceType = a.getResourceType() == RESOURCE_TYPE_PROCESS_DEFINITION;
    if (hasPermissions && grantPermission && processDefinitionResourceType) {
      String resourceId = a.getResourceId();
      if (!resourceId.isEmpty()) {
        definitionDefinitionAuthorizations.authorizeDefinition(resourceId);
      }
    }
  }

  private void removeAuthorizationForAllDefinitions(AuthorizationDto a,
                                                    DefinitionAuthorizations definitionDefinitionAuthorizations) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean revokePermission = a.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean processDefinitionResourceType = a.getResourceType() == RESOURCE_TYPE_PROCESS_DEFINITION;
    if (hasPermissions && revokePermission && processDefinitionResourceType) {
      String resourceId = a.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        definitionDefinitionAuthorizations.revokeToSeeAllDefinitions();
      }
    }
  }

  private void removeAuthorizationForProhibitedDefinition(AuthorizationDto a,
                                                          DefinitionAuthorizations definitionDefinitionAuthorizations) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean revokePermission = a.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean processDefinitionResourceType = a.getResourceType() == RESOURCE_TYPE_PROCESS_DEFINITION;
    if (hasPermissions && revokePermission && processDefinitionResourceType) {
      String resourceId = a.getResourceId();
      if (!resourceId.isEmpty()) {
        definitionDefinitionAuthorizations.prohibitDefinition(resourceId);
      }
    }
  }

  private class DefinitionAuthorizations {

    private boolean canSeeAll = false;
    private Set<String> authorizedDefinitions = new HashSet<>();
    private Set<String> prohibitedDefinitions = new HashSet<>();

    void grantToSeeAllDefinitions() {
      canSeeAll = true;
      prohibitedDefinitions.clear();
      authorizedDefinitions.clear();
    }

    void revokeToSeeAllDefinitions() {
      canSeeAll = false;
      authorizedDefinitions.clear();
      prohibitedDefinitions.clear();
    }

    void authorizeDefinition(String authorizedDefinition) {
      authorizedDefinitions.add(authorizedDefinition);
      prohibitedDefinitions.remove(authorizedDefinition);
    }

    void prohibitDefinition(String prohibitedDefinition) {
      prohibitedDefinitions.add(prohibitedDefinition);
      authorizedDefinitions.remove(prohibitedDefinition);
    }

    boolean isAuthorizedToSeeDefinition(String definition) {
      if (canSeeAll) {
        return !prohibitedDefinitions.contains(definition);
      } else {
        return authorizedDefinitions.contains(definition);
      }
    }
  }
}
