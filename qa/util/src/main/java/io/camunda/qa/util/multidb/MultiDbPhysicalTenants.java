/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.jspecify.annotations.NullMarked;

/**
 * Declares one or more non-default physical tenants that {@link CamundaMultiDBExtension} should
 * provision when the test runs. Each tenant gets its own isolated RDBMS-H2 database, a seeded
 * {@code <tenantId>-admin} user, and an {@code admin} default role for that user.
 *
 * <p>Only valid in combination with {@link MultiDbTest} and an RDBMS database type (RDBMS-only,
 * because per-PT secondary-storage schema init is not available for ES/OS yet).
 *
 * <p>The extension injects a {@code static MultiPhysicalTenantClients} field on the test class,
 * which provides per-PT admin clients via {@link MultiPhysicalTenantClients#admin(String)}.
 *
 * <pre>{@code
 * @MultiDbTest
 * @MultiDbPhysicalTenants({"tenanta", "tenantb"})
 * final class MyMultiPtIT {
 *
 *   @MultiDbTestApplication
 *   static final TestStandaloneBroker BROKER =
 *       new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();
 *
 *   static MultiPhysicalTenantClients PT_CLIENTS;
 *
 *   @Test
 *   void shouldIsolate() {
 *     // the holder owns the client lifecycle; don't close it here
 *     final CamundaClient admin = PT_CLIENTS.admin("tenanta");
 *     // ...
 *   }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@NullMarked
public @interface MultiDbPhysicalTenants {

  /**
   * Non-default physical tenant IDs to provision. Must not include {@code "default"} (the default
   * PT is always implicit). Each declared tenant gets its own isolated RDBMS schema, a seeded
   * {@code <id>-admin} user, and an {@code admin} default role for that user.
   */
  String[] value();
}
