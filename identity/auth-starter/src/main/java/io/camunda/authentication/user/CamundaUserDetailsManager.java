/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.user;

import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;

@Component
@Profile("auth-basic")
public class CamundaUserDetailsManager extends JdbcUserDetailsManager {
  public CamundaUserDetailsManager(final DataSource dataSource) {
    super(dataSource);
    setEnableGroups(true);
    setEnableAuthorities(true);
  }
}
