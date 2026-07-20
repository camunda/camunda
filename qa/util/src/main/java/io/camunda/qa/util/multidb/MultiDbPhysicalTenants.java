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
import org.junit.jupiter.api.Tag;

/**
 * Declares one or more non-default physical tenants that {@link CamundaMultiDBExtension} should
 * provision when the test runs. Each tenant gets its own isolated secondary storage, a seeded
 * {@code <tenantId>-admin} user, and an {@code admin} default role for that user.
 *
 * <p>The storage-isolation primitive is dialect-specific: Elasticsearch gives each tenant a
 * per-tenant index prefix on the shared cluster; {@code RDBMS_H2} gives each tenant a fresh
 * dedicated in-memory database; PostgreSQL/Aurora, MySQL/MariaDB and SQL Server give each tenant a
 * dedicated namespace named {@code <basePrefix>_<tenantId>} (a schema or database); and Oracle
 * gives each tenant a per-tenant table prefix in the shared schema (a dedicated user would need DBA
 * and is rejected by the production isolation check). See {@link PhysicalTenantSchemaProvisioner}
 * for the per-dialect details.
 *
 * <p>Only valid in combination with {@link MultiDbTest} and an RDBMS or Elasticsearch database type
 * (per-PT secondary-storage provisioning is not available for OpenSearch yet).
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
@Tag("multi-db-physical-tenants")
public @interface MultiDbPhysicalTenants {

  /**
   * Non-default physical tenant IDs to provision. Must not include {@code "default"} (the default
   * PT is always implicit). Each declared tenant gets its own isolated namespace (a fresh in-memory
   * database on {@code RDBMS_H2}; a dedicated schema or database on PostgreSQL/Aurora,
   * MySQL/MariaDB and SQL Server; a per-tenant table prefix in the shared schema on Oracle), a
   * seeded {@code <id>-admin} user, and an {@code admin} default role for that user.
   *
   * <p>IDs are embedded into SQL identifiers, so they must be lowercase alphanumeric (starting with
   * a letter) and short enough to satisfy the strictest dialect's identifier-length limit (the
   * namespace {@code <basePrefix>_<id>} must fit Oracle's 30-character cap); the extension
   * validates this up front.
   */
  String[] value();
}
