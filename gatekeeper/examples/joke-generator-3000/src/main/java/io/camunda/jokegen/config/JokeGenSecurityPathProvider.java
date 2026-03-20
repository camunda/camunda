package io.camunda.jokegen.config;

import io.camunda.gatekeeper.spi.SecurityPathProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class JokeGenSecurityPathProvider implements SecurityPathProvider {

  @Override
  public Set<String> apiPaths() {
    return Set.of("/api/**");
  }

  @Override
  public Set<String> unprotectedApiPaths() {
    return Set.of();
  }

  @Override
  public Set<String> unprotectedPaths() {
    return Set.of("/", "/error", "/css/**", "/js/**", "/images/**", "/actuator/health");
  }

  @Override
  public Set<String> webappPaths() {
    return Set.of("/jokes/**", "/login/**", "/logout", "/sso-callback", "/oauth2/**", "/post-logout");
  }

  @Override
  public Set<String> webComponentNames() {
    return Set.of("joke-generator");
  }
}
