/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for a user definition, that is picked up by the {@link
 * CamundaMultiDbExtension}. This is to clearly communicate that this user definition,
 * will be consumed and created (related permissions) by the {@link CamundaMultiDbExtension}.
 *
 *  <pre>{@code
 *  @Tag("multi-db-test")
 *  final class MyAuthMultiDbTest {
 *
 *    static final TestStandaloneBroker BROKER =
 *        new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();
 *
 *    @RegisterExtension
 *    static final CamundaMultiDBExtension EXTENSION = new CamundaMultiDBExtension(BROKER);
 *
 *    private static final String ADMIN = "admin";
 *
 *    @UserDefinition
 *    private static final User ADMIN_USER =
 *      new User(ADMIN,
 *               "password",
 *               List.of(new Permissions(AUTHORIZATION, PermissionTypeEnum.READ, List.of("*"))));
 *
 *    @Test
 *    void shouldMakeUseOfClient(@Authenticated(ADMIN) final CamundaClient adminClient) {
 *      // given
 *      // ... set up
 *
 *      // when
 *      topology = adminClient.newTopologyRequest().send().join();
 *
 *      // then
 *      assertThat(topology.getClusterSize()).isEqualTo(1);
 *    }
 *  }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UserDefinition {}
