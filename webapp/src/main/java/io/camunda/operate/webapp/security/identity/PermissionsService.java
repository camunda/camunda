/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.operate.webapp.security.OperateProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Profile(OperateProfileService.IDENTITY_AUTH_PROFILE)
@Component
public class PermissionsService {

  public static final String RESOURCE_KEY_ALL = "*";
  public static final String RESOURCE_TYPE_PROCESS_DEFINITION = "process-definition";

  private static final Logger log = LoggerFactory.getLogger(PermissionsService.class);

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
   * @param bpmnProcessId              bpmnProcessId
   * @param includeWildcardPermissions true to include the wildcard permission, false to not include them
   * @return Identity permissions for the given bpmnProcessId
   */
  public Set<String> getProcessDefinitionPermission(String bpmnProcessId, boolean includeWildcardPermissions) {

    Set<String> permissions = new HashSet<>();

    getIdentityAuthorizations().stream()
        .filter(x -> Objects.equals(x.getResourceKey(), bpmnProcessId) && Objects.equals(x.getResourceType(), RESOURCE_TYPE_PROCESS_DEFINITION))
        .findFirst()
        .ifPresent(x -> {
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
   * getIdentityAuthorizations
   */
  private List<IdentityAuthorization> getIdentityAuthorizations() {
    List<IdentityAuthorization> list = null;
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof IdentityAuthentication) {
      list = ((IdentityAuthentication) authentication).getAuthorizations();
    }
    return (list == null) ? new ArrayList<>() : list;
  }
}
