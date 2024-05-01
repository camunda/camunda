package io.camunda.identity.security.service;

import io.camunda.identity.security.record.CamundaUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@Profile("identity-oidc-auth")
public class OidcCurrentUserService implements CurrentUserService {
  @Override
  public CamundaUser getCurrentUserDetails() {
    var authentication = (OidcUser) getAuthentication().getPrincipal();
    return new CamundaUser(
        authentication.getPreferredUsername(),
        authentication.getAuthorities().stream().map(Object::toString).toList()
    );
  }

  private Authentication getAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
