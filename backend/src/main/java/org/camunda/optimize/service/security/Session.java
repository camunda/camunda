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
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
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

  public boolean isAuthorizedToSeeProcessDefinition(final String processDefinitionKey) {
    return isAuthorizedToSeeDefinition(processDefinitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);
  }

  public boolean isAuthorizedToSeeDecisionDefinition(final String decisionDefinitionKey) {
    return isAuthorizedToSeeDefinition(decisionDefinitionKey, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  private boolean isAuthorizedToSeeDefinition(final String decisionDefinitionKey, final int resourceType) {
    if (decisionDefinitionKey == null || decisionDefinitionKey.isEmpty()) {
      return true;
    }

    final DefinitionAuthorizations definitionDefinitionAuthorizations = buildDefinitionAuthorizations(resourceType);
    return definitionDefinitionAuthorizations.isAuthorizedToSeeDefinition(decisionDefinitionKey);
  }

  private DefinitionAuthorizations buildDefinitionAuthorizations(int resourceType) {
    final DefinitionAuthorizations authorizations = new DefinitionAuthorizations();

    // NOTE: the order is essential here to make sure that
    // the revoking of definition permissions works correctly

    // global authorizations
    addGloballyAuthorizedDefinitions(
      authorizations, definitionAuthorizations.getAllDefinitionAuthorizations(), resourceType
    );

    // group authorizations
    addDefinitionAuthorizations(authorizations, definitionAuthorizations.getGroupAuthorizations(), resourceType);

    // user authorizations
    addDefinitionAuthorizations(authorizations, definitionAuthorizations.getUserAuthorizations(), resourceType);

    return authorizations;
  }

  private void addDefinitionAuthorizations(final DefinitionAuthorizations definitionDefinitionAuthorizations,
                                           final List<AuthorizationDto> groupAuthorizations,
                                           int resourceType) {
    removeAuthorizationsForAllDefinitions(groupAuthorizations, definitionDefinitionAuthorizations, resourceType);
    addAuthorizationsForAllDefinitions(groupAuthorizations, definitionDefinitionAuthorizations, resourceType);
    removeAuthorizationsForProhibitedDefinition(groupAuthorizations, definitionDefinitionAuthorizations, resourceType);
    addAuthorizationsForSingleDefinitions(groupAuthorizations, definitionDefinitionAuthorizations, resourceType);
  }

  private void addGloballyAuthorizedDefinitions(DefinitionAuthorizations definitionDefinitionAuthorizations,
                                                List<AuthorizationDto> authorizations,
                                                int resourceType) {
    authorizations.forEach(a -> addGloballyAuthorizedDefinition(a, definitionDefinitionAuthorizations, resourceType));
  }

  private void addAuthorizationsForAllDefinitions(List<AuthorizationDto> authorizations,
                                                  DefinitionAuthorizations definitionDefinitionAuthorizations,
                                                  int resourceType) {
    authorizations.forEach(a -> addAuthorizationForAllDefinitions(a, definitionDefinitionAuthorizations, resourceType));
  }

  private void addAuthorizationsForSingleDefinitions(List<AuthorizationDto> authorizations,
                                                     DefinitionAuthorizations definitionDefinitionAuthorizations,
                                                     int resourceType) {
    authorizations.forEach(
      a -> addAuthorizationForDefinition(a, definitionDefinitionAuthorizations, resourceType)
    );
  }

  private void removeAuthorizationsForAllDefinitions(List<AuthorizationDto> authorizations,
                                                     DefinitionAuthorizations definitionDefinitionAuthorizations,
                                                     int resourceType) {
    authorizations.forEach(
      a -> removeAuthorizationForAllDefinitions(a, definitionDefinitionAuthorizations, resourceType)
    );
  }

  private void removeAuthorizationsForProhibitedDefinition(List<AuthorizationDto> authorizations,
                                                           DefinitionAuthorizations definitionDefinitionAuthorizations,
                                                           int resourceType) {
    authorizations.forEach(
      a -> removeAuthorizationForProhibitedDefinition(a, definitionDefinitionAuthorizations, resourceType)
    );
  }

  private void addGloballyAuthorizedDefinition(AuthorizationDto a,
                                               DefinitionAuthorizations definitionDefinitionAuthorizations,
                                               int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean globalGrantPermission = a.getType() == AUTHORIZATION_TYPE_GLOBAL;
    boolean processDefinitionResourceType = a.getResourceType() == resourceType;
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
                                                 DefinitionAuthorizations definitionDefinitionAuthorizations,
                                                 int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean processDefinitionResourceType = a.getResourceType() == resourceType;
    if (hasPermissions && grantPermission && processDefinitionResourceType) {
      String resourceId = a.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        definitionDefinitionAuthorizations.grantToSeeAllDefinitions();
      }
    }
  }

  private void addAuthorizationForDefinition(AuthorizationDto a,
                                             DefinitionAuthorizations definitionDefinitionAuthorizations,
                                             int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean grantPermission = a.getType() == AUTHORIZATION_TYPE_GRANT;
    boolean processDefinitionResourceType = a.getResourceType() == resourceType;
    if (hasPermissions && grantPermission && processDefinitionResourceType) {
      String resourceId = a.getResourceId();
      if (!resourceId.isEmpty()) {
        definitionDefinitionAuthorizations.authorizeDefinition(resourceId);
      }
    }
  }

  private void removeAuthorizationForAllDefinitions(AuthorizationDto a,
                                                    DefinitionAuthorizations definitionDefinitionAuthorizations,
                                                    int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean revokePermission = a.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean processDefinitionResourceType = a.getResourceType() == resourceType;
    if (hasPermissions && revokePermission && processDefinitionResourceType) {
      String resourceId = a.getResourceId();
      if (resourceId.trim().equals(ALL_RESOURCES_RESOURCE_ID)) {
        definitionDefinitionAuthorizations.revokeToSeeAllDefinitions();
      }
    }
  }

  private void removeAuthorizationForProhibitedDefinition(AuthorizationDto a,
                                                          DefinitionAuthorizations definitionDefinitionAuthorizations,
                                                          int resourceType) {
    boolean hasPermissions = hasCorrectPermissions(a);
    boolean revokePermission = a.getType() == AUTHORIZATION_TYPE_REVOKE;
    boolean processDefinitionResourceType = a.getResourceType() == resourceType;
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
