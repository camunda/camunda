package io.camunda.jokegen.auth;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.PrincipalType;
import io.camunda.gatekeeper.spi.MembershipResolver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("oidc")
public final class OidcMembershipResolver implements MembershipResolver {

  @Override
  @SuppressWarnings("unchecked")
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    List<String> roles = Collections.emptyList();
    if (tokenClaims != null) {
      final Object realmAccessObj = tokenClaims.get("realm_access");
      if (realmAccessObj instanceof Map<?, ?> realmAccess) {
        final Object rolesObj = realmAccess.get("roles");
        if (rolesObj instanceof List<?> rolesList) {
          roles = rolesList.stream().map(Object::toString).toList();
        }
      }
    }
    final List<String> resolvedRoles = roles;
    return CamundaAuthentication.of(
        b -> b.user(principalId).roleIds(resolvedRoles).claims(tokenClaims));
  }
}
