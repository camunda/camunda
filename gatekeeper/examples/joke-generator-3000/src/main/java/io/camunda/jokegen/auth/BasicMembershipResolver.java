package io.camunda.jokegen.auth;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.PrincipalType;
import io.camunda.gatekeeper.spi.MembershipResolver;
import io.camunda.jokegen.repository.UserRoleRepository;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("basic")
public final class BasicMembershipResolver implements MembershipResolver {

  private final UserRoleRepository userRoleRepository;

  public BasicMembershipResolver(final UserRoleRepository userRoleRepository) {
    this.userRoleRepository = userRoleRepository;
  }

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    final List<String> roles = userRoleRepository.findRoleNamesByUsername(principalId);
    return CamundaAuthentication.of(b -> b.user(principalId).roleIds(roles));
  }
}
