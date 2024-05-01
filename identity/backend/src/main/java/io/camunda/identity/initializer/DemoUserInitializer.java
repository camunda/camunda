/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.initializer;

import io.camunda.identity.security.service.IdentityUserDetailsManager;
import jakarta.annotation.PostConstruct;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Component;

@Component
@Profile("identity-local-auth")
public class DemoUserInitializer {
  private final IdentityUserDetailsManager userDetailsManager;

  public DemoUserInitializer(final DataSource dataSource) {
    userDetailsManager = new IdentityUserDetailsManager(dataSource);
  }

  @PostConstruct
  public void setupUsers() {
    final UserDetails user =
        User.builder()
            .username("demo")
            .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("demo"))
            .roles("USER")
            .build();
    userDetailsManager.createUser(user);
    userDetailsManager.createGroup(
        "group1", "org1", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    userDetailsManager.addUserToGroup("demo", "group1");
  }
}
