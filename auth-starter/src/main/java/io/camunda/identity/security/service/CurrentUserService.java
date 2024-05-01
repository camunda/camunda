package io.camunda.identity.security.service;

import io.camunda.identity.security.record.CamundaUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

public interface CurrentUserService {
  CamundaUser getCurrentUserDetails();
}
