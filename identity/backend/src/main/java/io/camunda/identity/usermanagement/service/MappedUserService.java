/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.identity.security.CamundaUserDetailsManager;
import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.model.MappedUser;
import io.camunda.identity.usermanagement.model.MappingRule;
import io.camunda.identity.usermanagement.repository.MappedUserRepository;
import io.camunda.identity.usermanagement.repository.MappingRuleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MappedUserService {

  private final MappingRuleRepository mappingRuleRepository;

  private final CamundaUserDetailsManager camundaUserDetailsManager;

  private final PasswordEncoder passwordEncoder;

  private final UserService userService;

  private final MappedUserRepository mappedUserRepository;

  public MappedUserService(
      final MappingRuleRepository mappingRuleRepository,
      final CamundaUserDetailsManager userDetailsManager,
      final PasswordEncoder passwordEncoder,
      final UserService userService,
      final MappedUserRepository mappedUserRepository) {
    this.mappingRuleRepository = mappingRuleRepository;
    this.camundaUserDetailsManager = userDetailsManager;
    this.passwordEncoder = passwordEncoder;
    this.userService = userService;
    this.mappedUserRepository = mappedUserRepository;
  }

  private Stream<MappingRule> fetchMappingRules(final Map<String, Object> tokenClaims) {
    return mappingRuleRepository.findAll().stream()
        .filter(jwtMappingRule -> tokenClaims.containsKey(jwtMappingRule.getClaimName()))
        .filter(
            jwtMappingRule ->
                switch (jwtMappingRule.getOperator()) {
                  case EQUALS -> {
                    var claimValue = tokenClaims.get(jwtMappingRule.getClaimName()).toString();
                    yield claimValue != null && claimValue.equals(jwtMappingRule.getClaimValue());
                  }
                  case CONTAINS -> {
                    var claimValues = (List<String>) tokenClaims.get(jwtMappingRule.getClaimName());
                    yield claimValues != null
                        && claimValues.contains(jwtMappingRule.getClaimValue());
                  }
                });
  }

  public List<MappedUser> loadMappedUsers(final OidcUser oidcUser) {
    return fetchMappingRules(oidcUser.getClaims()).map(MappingRule::getMappedUser).toList();
  }

  public void createMappedUser(MappingRule mappingRule) {
    final UserDetails userDetails =
        User.withUsername(mappingRule.getName())
            .password(UUID.randomUUID().toString() + UUID.randomUUID())
            .passwordEncoder(passwordEncoder::encode)
            .disabled(false)
            .roles("OIDC_USER")
            .build();
    camundaUserDetailsManager.createUser(userDetails);
    CamundaUser user = userService.findUserByUsername(userDetails.getUsername());
    MappedUser mappedUser = new MappedUser();
    mappedUser.setId(user.getId());
    mappedUserRepository.save(mappedUser);
    mappingRule.setMappedUser(mappedUser);
    mappingRuleRepository.save(mappingRule);
  }
}
