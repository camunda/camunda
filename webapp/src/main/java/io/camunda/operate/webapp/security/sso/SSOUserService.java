/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.sso;

import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.UserService;

import java.util.List;
import java.util.Map;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.security.sso.model.ClusterMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import com.auth0.jwt.interfaces.Claim;

@Component
@Profile(OperateProfileService.SSO_AUTH_PROFILE)
public class SSOUserService implements UserService<AbstractAuthenticationToken> {

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private C8ConsoleService c8ConsoleService;

  @Override
  public UserDto createUserDtoFrom(
      final AbstractAuthenticationToken abstractAuthentication) {
    if (abstractAuthentication instanceof TokenAuthentication) {
      return getUserDtoFor((TokenAuthentication) abstractAuthentication);
    } else if (abstractAuthentication instanceof JwtAuthenticationToken){
      return getUserDtoFor((JwtAuthenticationToken) abstractAuthentication);
    } else {
      return null;
    }
  }

  private UserDto getUserDtoFor(JwtAuthenticationToken authentication) {
    // Token is already validated in CCSaaSJwtAuthenticationTokenValidator
    // ,and we don't have permission yet.
    final List<Permission> permissions = List.of(Permission.READ, Permission.WRITE);
    return new UserDto()
        .setUserId(authentication.getName())
        .setDisplayName(authentication.getName())
        .setCanLogout(false)
        .setPermissions(permissions);
  }

  private UserDto getUserDtoFor(final TokenAuthentication authentication) {
    Map<String, Claim> claims = authentication.getClaims();
    String name = "No name";
    if (claims.containsKey(operateProperties.getAuth0().getNameKey())) {
      name = claims.get(operateProperties.getAuth0().getNameKey()).asString();
    }
    final ClusterMetadata clusterMetadata = c8ConsoleService.getClusterMetadata();
    Map<ClusterMetadata.AppName,String> appNames2Urls = Map.of();
    if ( clusterMetadata != null ) {
      appNames2Urls = clusterMetadata.getUrls();
    }
    return new UserDto().setUserId(authentication.getName()).setDisplayName(name).setCanLogout(false)
        .setPermissions(authentication.getPermissions())
        .setRoles(authentication.getRoles(operateProperties.getAuth0().getOrganizationsKey()))
        .setSalesPlanType(authentication.getSalesPlanType()).setC8Links(appNames2Urls);
  }
}
