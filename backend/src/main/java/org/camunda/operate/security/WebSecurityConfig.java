package org.camunda.operate.security;

import javax.json.Json;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.entity.ContentType;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * @author Svetlana Dorokhova.
 */

@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {


  @Bean
  public UserDetailsService userDetailsService() {
    InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
    manager.createUser(User.builder().username("demo")
      .password(passwordEncoder().encode("demo"))
      .roles("USER").build());
    return manager;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Override
  public void configure(HttpSecurity http) throws Exception {
    http
      .csrf().disable()
      .cors()
      .and()
      .authorizeRequests()
        .anyRequest().authenticated()
      .and()
        .formLogin()
          .successHandler(successHandler())
          .failureHandler(failureHandler())
        .permitAll()
      .and()
        .logout()
        .logoutSuccessHandler(logoutSuccessHandler())
        .permitAll()
        .invalidateHttpSession(true)
        .deleteCookies("JSESSIONID")
      .and()
        .exceptionHandling().authenticationEntryPoint(authenticationEntryPoint());
  }

  private LogoutSuccessHandler logoutSuccessHandler() {
    return (HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication) ->
      httpServletResponse.setStatus(200);
  }

  private AuthenticationEntryPoint authenticationEntryPoint() {
    return (HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AuthenticationException ex) -> {
      httpServletResponse.getWriter().append(
        Json.createObjectBuilder()
          .add("exceptionMessage", ex.getMessage())
          .build().toString());
      httpServletResponse.setStatus(401);
      httpServletResponse.setContentType(ContentType.APPLICATION_JSON.getMimeType());
    };
  }

  private AuthenticationSuccessHandler successHandler() {
    return (HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication) -> {
      final String name = SecurityContextHolder.getContext().getAuthentication().getName();
      httpServletResponse.getWriter().append(
        Json.createObjectBuilder()
          .add("id", 134L)
          .add("name", name)
          .build().toString());

      httpServletResponse.setStatus(200);
      httpServletResponse.setContentType(ContentType.APPLICATION_JSON.getMimeType());
    };
  }

  private AuthenticationFailureHandler failureHandler() {
    return (HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, AuthenticationException ex) -> {
      httpServletResponse.getWriter().append(
        Json.createObjectBuilder().add("exceptionMessage", ex.getMessage()).build().toString());
      httpServletResponse.setStatus(401);
      httpServletResponse.setContentType(ContentType.APPLICATION_JSON.getMimeType());
    };
  }

}