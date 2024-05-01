package io.camunda.identity.security.service;

import io.camunda.identity.security.record.CamundaUser;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Profile("identity-local-auth")
public class LocalCurrentUserService implements CurrentUserService {
  @Override
  public CamundaUser getCurrentUserDetails() {
    var authentication = getAuthentication();
    return new CamundaUser(
        authentication.getName(),
        authentication.getAuthorities().stream().map(Object::toString).toList()
    );
  }

  private Authentication getAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
