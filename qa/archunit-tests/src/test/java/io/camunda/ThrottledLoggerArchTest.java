/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaStaticInitializer;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.camunda.zeebe.util.logging.ThrottledLogger;

@AnalyzeClasses(
    packages = {"io.camunda", "io.atomix"},
    importOptions = ImportOption.DoNotIncludeTests.class)
public final class ThrottledLoggerArchTest {

  @ArchTest
  static final ArchRule REQUIRE_THROTTLED_LOGGERS_PER_INSTANCE =
      noClasses()
          .should()
          .callConstructorWhere(
              DescribedPredicate.<JavaConstructorCall>describe(
                  "a ThrottledLogger is created in a static initializer",
                  call ->
                      call.getTargetOwner().isAssignableTo(ThrottledLogger.class)
                          && call.getOrigin() instanceof JavaStaticInitializer))
          .because(
              "a ThrottledLogger keeps the state of its throttle, and a static one shares it with"
                  + " every instance in the JVM, so that one partition, or one broker of a test"
                  + " cluster, suppresses the messages of another; hold it per instance, and pass"
                  + " a shared instance explicitly where a JVM-wide limit is wanted");
}
