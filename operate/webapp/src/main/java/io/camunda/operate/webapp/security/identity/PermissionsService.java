/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.sso.TokenAuthentication;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class PermissionsService {

  public static final String RESOURCE_KEY_ALL = "*";
  public static final String RESOURCE_TYPE_PROCESS_DEFINITION = "process-definition";
  public static final String RESOURCE_TYPE_DECISION_DEFINITION = "decision-definition";

  private static final Logger logger = LoggerFactory.getLogger(PermissionsService.class);

  private OperateProperties operateProperties;

  public PermissionsService(OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
  }

  @PostConstruct
  public void logCreated() {
    logger.debug("PermissionsService bean created.");
  }

  /**
   * getProcessDefinitionPermission
   *
   * @param bpmnProcessId bpmnProcessId
   * @return Identity permissions for the given bpmnProcessId, including wildcard permissions
   */
  public Set<String> getProcessDefinitionPermission(String bpmnProcessId) {
    return getProcessDefinitionPermission(bpmnProcessId, true);
  }

  /**
   * getProcessDefinitionPermission
   *
   * @param bpmnProcessId bpmnProcessId
   * @param includeWildcardPermissions true to include the wildcard permission, false to not include
   *     them
   * @return Identity permissions for the given bpmnProcessId
   */
  public Set<String> getProcessDefinitionPermission(
      String bpmnProcessId, boolean includeWildcardPermissions) {

    Set<String> permissions = new HashSet<>();

    getIdentityAuthorizations().stream()
        .filter(
            x ->
                Objects.equals(x.getResourceKey(), bpmnProcessId)
                    && Objects.equals(x.getResourceType(), RESOURCE_TYPE_PROCESS_DEFINITION))
        .findFirst()
        .ifPresent(
            x -> {
              if (x.getPermissions() != null) {
                permissions.addAll(x.getPermissions());
              }
            });

    if (includeWildcardPermissions) {
      permissions.addAll(getProcessDefinitionPermission(RESOURCE_KEY_ALL, false));
    }

    return permissions;
  }

  /**
   * getDecisionDefinitionPermission
   *
   * @param decisionId decisionId
   * @return Identity permissions for the given decisionId, including wildcard permissions
   */
  public Set<String> getDecisionDefinitionPermission(String decisionId) {
    return getDecisionDefinitionPermission(decisionId, true);
  }

  /**
   * getDecisionDefinitionPermission
   *
   * @param decisionId decisionId
   * @param includeWildcardPermissions true to include the wildcard permission, false to not include
   *     them
   * @return Identity permissions for the given decisionId
   */
  public Set<String> getDecisionDefinitionPermission(
      String decisionId, boolean includeWildcardPermissions) {

    Set<String> permissions = new HashSet<>();

    getIdentityAuthorizations().stream()
        .filter(
            x ->
                Objects.equals(x.getResourceKey(), decisionId)
                    && Objects.equals(x.getResourceType(), RESOURCE_TYPE_DECISION_DEFINITION))
        .findFirst()
        .ifPresent(
            x -> {
              if (x.getPermissions() != null) {
                permissions.addAll(x.getPermissions());
              }
            });

    if (includeWildcardPermissions) {
      permissions.addAll(getDecisionDefinitionPermission(RESOURCE_KEY_ALL, false));
    }

    return permissions;
  }

  /**
   * hasPermissionForProcess
   *
   * @return true if the user has the given permission for the process
   */
  public boolean hasPermissionForProcess(String bpmnProcessId, IdentityPermission permission) {

    if (!permissionsEnabled()) {
      return true;
    }
    if (permission == null) {
      throw new IllegalStateException("Identity permission can't be null");
    }
    return getProcessDefinitionPermission(bpmnProcessId).stream()
        .anyMatch(x -> x.equalsIgnoreCase(permission.toString()));
  }

  /**
   * hasPermissionForDecision
   *
   * @return true if the user has the given permission for the decision
   */
  public boolean hasPermissionForDecision(String decisionId, IdentityPermission permission) {

    if (!permissionsEnabled()) {
      return true;
    }
    if (permission == null) {
      throw new IllegalStateException("Identity permission can't be null");
    }
    return getDecisionDefinitionPermission(decisionId).stream()
        .anyMatch(x -> x.equalsIgnoreCase(permission.toString()));
  }

  /** getIdentityAuthorizations */
  private List<IdentityAuthorization> getIdentityAuthorizations() {
    List<IdentityAuthorization> list = null;
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof IdentityAuthentication) {
      list = ((IdentityAuthentication) authentication).getAuthorizations();
      logger.debug("Following authorizations found for IdentityAuthentication: " + list);
    } else if (authentication instanceof TokenAuthentication) {
      list = ((TokenAuthentication) authentication).getAuthorizations();
      logger.debug("Following authorizations found for TokenAuthentication: " + list);
    } else {
      logger.error(
          "Unable to read resource based permissions. Unknown token type: "
              + authentication.getClass().getSimpleName(),
          new OperateRuntimeException());
    }
    return (list == null) ? new ArrayList<>() : list;
  }

  private boolean permissionsEnabled() {
    return operateProperties.getIdentity().isResourcePermissionsEnabled() && !isJwtToken();
  }

  /**
   * Resource based permissions are not yet supported for JwtAuthenticationToken.
   *
   * @return
   */
  private boolean isJwtToken() {
    return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken;
  }

  /**
   * getProcessesWithPermission
   *
   * @return processes for which the user has the given permission; the result matches either all
   *     processes, or a list of bpmnProcessId
   */
  public ResourcesAllowed getProcessesWithPermission(IdentityPermission permission) {
    if (permission == null) {
      throw new IllegalStateException("Identity permission can't be null");
    }

    if (permissionsEnabled()) {
      List<IdentityAuthorization> processAuthorizations =
          getIdentityAuthorizations().stream()
              .filter(x -> Objects.equals(x.getResourceType(), RESOURCE_TYPE_PROCESS_DEFINITION))
              .collect(Collectors.toList());
      Set<String> ids = new HashSet<>();
      for (IdentityAuthorization authorization : processAuthorizations) {
        if (authorization.getPermissions() != null
            && authorization.getPermissions().contains(permission.name())) {
          if (RESOURCE_KEY_ALL.equals(authorization.getResourceKey())) {
            return ResourcesAllowed.all();
          }
          ids.add(authorization.getResourceKey());
        }
      }
      return ResourcesAllowed.withIds(ids);
    }
    return ResourcesAllowed.all();
  }

  /**
   * getDecisionsWithPermission
   *
   * @return decisions for which the user has the given permission; the result matches either all
   *     decisions, or a list of decisionId
   */
  public ResourcesAllowed getDecisionsWithPermission(IdentityPermission permission) {
    if (permission == null) {
      throw new IllegalStateException("Identity permission can't be null");
    }

    if (permissionsEnabled()) {
      List<IdentityAuthorization> decisionAuthorizations =
          getIdentityAuthorizations().stream()
              .filter(x -> Objects.equals(x.getResourceType(), RESOURCE_TYPE_DECISION_DEFINITION))
              .collect(Collectors.toList());
      Set<String> ids = new HashSet<>();
      for (IdentityAuthorization authorization : decisionAuthorizations) {
        if (authorization.getPermissions() != null
            && authorization.getPermissions().contains(permission.name())) {
          if (RESOURCE_KEY_ALL.equals(authorization.getResourceKey())) {
            return ResourcesAllowed.all();
          }
          ids.add(authorization.getResourceKey());
        }
      }
      return ResourcesAllowed.withIds(ids);
    }
    return ResourcesAllowed.all();
  }

  /** ResourcesAllowed */
  public static class ResourcesAllowed {
    private boolean all;
    private Set<String> ids;

    private ResourcesAllowed(boolean all, Set<String> ids) {
      this.all = all;
      this.ids = ids;
    }

    public static ResourcesAllowed all() {
      return new ResourcesAllowed(true, null);
    }

    public static ResourcesAllowed withIds(Set<String> ids) {
      return new ResourcesAllowed(false, ids);
    }

    @Override
    public int hashCode() {
      return Objects.hash(all, ids);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ResourcesAllowed that = (ResourcesAllowed) o;
      return all == that.all && Objects.equals(ids, that.ids);
    }

    /**
     * isAll
     *
     * @return true if all resources are allowed, false if only the ids are allowed
     */
    public boolean isAll() {
      return all;
    }

    /**
     * getIds
     *
     * @return ids of resources allowed in case not all are allowed
     */
    public Set<String> getIds() {
      return ids;
    }
  }
}
