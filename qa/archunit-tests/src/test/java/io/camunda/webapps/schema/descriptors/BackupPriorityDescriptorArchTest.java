/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.webapps.schema.descriptors.backup.BackupPriority;

/**
 * Enforces that every concrete ES/OS index descriptor in webapps-schema declares a backup priority
 * tier. Descriptors that do not implement {@link BackupPriority} are silently excluded from backup
 * and cause data loss on restore.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/55578">Issue #55578</a>
 */
@AnalyzeClasses(
    packages = "io.camunda.webapps.schema.descriptors",
    importOptions = DoNotIncludeTestsOrTestJars.class)
public final class BackupPriorityDescriptorArchTest {

  @ArchTest
  static final ArchRule ALL_DESCRIPTORS_MUST_IMPLEMENT_BACKUP_PRIORITY =
      ArchRuleDefinition.classes()
          .that(
              assignableTo(IndexDescriptor.class)
                  .and(
                      new DescribedPredicate<JavaClass>("are not abstract and not interfaces") {
                        @Override
                        public boolean test(final JavaClass input) {
                          return !input.getModifiers().contains(JavaModifier.ABSTRACT)
                              && !input.isInterface();
                        }
                      }))
          .should()
          .implement(BackupPriority.class)
          .because(
              "every ES/OS index descriptor in webapps-schema must declare a backup priority"
                  + " tier (Prio1Backup-Prio4Backup); descriptors missing this contract are"
                  + " silently excluded from backup and cause data loss on restore");
}
