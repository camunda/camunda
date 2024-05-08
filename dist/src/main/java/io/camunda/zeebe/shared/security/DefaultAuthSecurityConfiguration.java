package io.camunda.zeebe.shared.security;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.shared.management.ConditionalOnManagementContext;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Profile("auth")
@EnableWebSecurity
@Configuration(proxyBeanMethods = false)
public final class DefaultAuthSecurityConfiguration {

  @Bean
  @ConditionalOnRestGatewayEnabled
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain restGatewaySecurity(final HttpSecurity http) throws Exception {
    return http.securityMatcher("/v1/topology", "/v1/user-tasks/**")
        .csrf()
        .disable()
        .authorizeRequests((authz) -> authz.anyRequest().permitAll())
        .build();
  }

  private static HttpSecurity configureSecurity(final HttpSecurity http) throws Exception {
    return http.csrf(CsrfConfigurer::disable)
        .cors(CorsConfigurer::disable)
        .logout(LogoutConfigurer::disable)
        .formLogin(FormLoginConfigurer::disable)
        .httpBasic(HttpBasicConfigurer::disable)
        .anonymous(AnonymousConfigurer::disable);
  }

  @Profile("auth")
  @ConditionalOnManagementContext
  @EnableWebSecurity
  @ManagementContextConfiguration(value = ManagementContextType.ANY, proxyBeanMethods = false)
  public static final class ManagementSecurityConfiguration {
    @Bean
    public SecurityFilterChain managementSecurity(final HttpSecurity http) throws Exception {
      return configureSecurity(http)
          .authorizeHttpRequests(spec -> spec.anyRequest().permitAll())
          .build();
    }
  }
}
