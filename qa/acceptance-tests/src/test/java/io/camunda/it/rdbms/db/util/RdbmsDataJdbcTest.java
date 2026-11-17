/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.ContextConfiguration;

/**
 * Composed test annotation for RDBMS integration tests that use Spring's {@link DataJdbcTest}
 * slice.
 *
 * <p>It bundles the common setup shared by tests under {@code io.camunda.it.rdbms.db}:
 *
 * <ul>
 *   <li>{@link DataJdbcTest} for the JDBC test slice.
 *   <li>{@link AutoConfigureTestDatabase} with {@link Replace#NONE} to prevent Spring Boot from
 *       replacing the production {@code DataSource} with an embedded one. The RDBMS layer manages
 *       its own datasource via {@code RdbmsDataSources}, and replacing it would cause the
 *       schema-creating {@code LiquibaseSchemaManager} and the MyBatis {@code SqlSessionFactory} to
 *       operate on different databases.
 *   <li>{@link ContextConfiguration} loading {@link RdbmsTestConfiguration} and {@link
 *       RdbmsConfiguration}.
 *   <li>{@link AutoConfigurationPackage} so component scanning picks up the production
 *       configuration.
 *   <li>{@link Tag} {@code "rdbms"} so tests are selected by the {@code rdbms} Maven profile.
 * </ul>
 *
 * <p>Tests that need additional properties can still declare their own {@code @TestPropertySource}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Tag("rdbms")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
public @interface RdbmsDataJdbcTest {}
