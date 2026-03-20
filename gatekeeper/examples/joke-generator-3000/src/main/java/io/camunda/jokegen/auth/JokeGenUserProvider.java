package io.camunda.jokegen.auth;

import io.camunda.gatekeeper.model.identity.CamundaUserInfo;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spi.CamundaUserProvider;
import io.camunda.jokegen.model.AppUser;
import io.camunda.jokegen.repository.AppUserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public final class JokeGenUserProvider implements CamundaUserProvider {

  private final CamundaAuthenticationProvider authProvider;
  private final AppUserRepository appUserRepository;
  private final String authMethod;

  public JokeGenUserProvider(
      final CamundaAuthenticationProvider authProvider,
      final AppUserRepository appUserRepository,
      @Value("${camunda.security.authentication.method}") final String authMethod) {
    this.authProvider = authProvider;
    this.appUserRepository = appUserRepository;
    this.authMethod = authMethod;
  }

  @Override
  public CamundaUserInfo getCurrentUser() {
    final var springAuth = SecurityContextHolder.getContext().getAuthentication();
    if (springAuth == null
        || !springAuth.isAuthenticated()
        || springAuth instanceof AnonymousAuthenticationToken) {
      return null;
    }
    final var auth = authProvider.getCamundaAuthentication();
    if (auth == null || auth.isAnonymous()) {
      return null;
    }
    final String username = auth.authenticatedUsername();
    String displayName = username;
    String email = null;

    if ("BASIC".equalsIgnoreCase(authMethod)) {
      final AppUser appUser = appUserRepository.findByUsername(username).orElse(null);
      if (appUser != null) {
        displayName = appUser.getDisplayName();
        email = appUser.getEmail();
      }
    } else {
      final var claims = auth.claims();
      if (claims != null) {
        final Object nameObj = claims.get("name");
        if (nameObj != null) {
          displayName = nameObj.toString();
        }
        final Object emailObj = claims.get("email");
        if (emailObj != null) {
          email = emailObj.toString();
        }
      }
    }

    return new CamundaUserInfo(
        displayName,
        username,
        email,
        List.of("joke-generator"),
        List.of(),
        List.of(),
        auth.authenticatedRoleIds(),
        null,
        Map.of(),
        true);
  }

  @Override
  public String getUserToken() {
    if ("BASIC".equalsIgnoreCase(authMethod)) {
      return null;
    }
    final var securityAuth = SecurityContextHolder.getContext().getAuthentication();
    if (securityAuth instanceof OAuth2AuthenticationToken oauthToken) {
      final var principal = oauthToken.getPrincipal();
      if (principal instanceof OidcUser oidcUser) {
        return oidcUser.getIdToken().getTokenValue();
      }
    }
    return null;
  }
}
