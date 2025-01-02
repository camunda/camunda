/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

public class CamundaOidcUserTest {
  @Test
  public void oidcUserIsSerializable() throws IOException, ClassNotFoundException {
    final var buffer = new ByteArrayOutputStream();
    final var out = new ObjectOutputStream(buffer);
    final var user =
        new CamundaOidcUser(
            new DefaultOidcUser(Set.of(), OidcIdToken.withTokenValue(".").subject("s").build()),
            Set.of("org1"),
            Set.of(1L, 2L, 3L),
            new AuthenticationContext(List.of(), List.of("app1"), List.of(), List.of("group1")));
    // when
    out.writeObject(user);
    final var in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()));
    final var result = in.readObject();
    assertThat(result).isEqualTo(user);
  }
}
