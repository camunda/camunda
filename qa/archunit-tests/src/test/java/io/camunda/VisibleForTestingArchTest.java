/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasName.AndFullName;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import java.util.Set;

/**
 * ArchUnit rules to enforce correct usage of the {@link io.camunda.zeebe.util.VisibleForTesting}
 * annotation.
 *
 * <p>Elements (classes, methods, fields) annotated with {@code @VisibleForTesting} must only be
 * accessed from test code or from within the same class (self-usage). This prevents accidental
 * usage in production code outside their intended scope.
 *
 * @see io.camunda.zeebe.util.VisibleForTesting
 */
@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.DoNotIncludeTests.class)
public class VisibleForTestingArchTest {

  static final List<String> PUBLIC_ELEMENTS =
      List.of(
          "io.camunda.db.rdbms.write.service.HistoryCleanupService.getHistoryCleanupInterval()",
          "io.camunda.db.rdbms.write.service.HistoryCleanupService.resolveBatchOperationTTL(io.camunda.search.entities.BatchOperationType)",
          "io.camunda.exporter.handlers.AuditLogHandler.getTransformer()",
          "io.camunda.exporter.rdbms.handlers.AuditLogExportHandler.getTransformer()",
          "io.camunda.zeebe.backup.client.api.BackupRequestHandler.takeBackup(long, io.camunda.zeebe.protocol.record.value.management.CheckpointType)",
          "io.camunda.zeebe.broker.exporter.repo.ExporterRepository.validateAndAddExporterDescriptor(java.lang.String, java.lang.Class, java.util.Map)",
          "io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector.getCommitPosition()",
          "io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerRegistry.createAllTransformers()",
          "io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration.GROUPS_API_DISABLED_ERROR_MESSAGE",
          "io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration.TENANTS_API_DISABLED_ERROR_MESSAGE",
          "io.camunda.zeebe.gateway.rest.config.ApiFiltersConfiguration.USERS_API_DISABLED_ERROR_MESSAGE",
          "io.camunda.zeebe.journal.fs.LibC.ofNativeLibrary(java.lang.String)",
          "io.camunda.zeebe.snapshots.impl.SnapshotMetrics.getTransferDuration(boolean)",
          "io.camunda.zeebe.util.VersionUtil.overrideVersionForTesting(java.lang.String)",
          "io.camunda.zeebe.util.VersionUtil.resetVersionForTesting()");

  //
  // Check that the extended visibility is only used for testing purposes
  //

  @ArchTest
  static final ArchRule CLASS_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      classes().that().areAnnotatedWith(VisibleForTesting.class).should(beAccessedBySelfOrTest());

  @ArchTest
  static final ArchRule METHOD_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      methods().that().areAnnotatedWith(VisibleForTesting.class).should(beAccessedBySelfOrTest());

  @ArchTest
  static final ArchRule FIELD_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      fields().that().areAnnotatedWith(VisibleForTesting.class).should(beAccessedBySelfOrTest());

  //
  // Check that the annotation is used to properly extend the visibility
  //

  @ArchTest
  static final ArchRule CLASS_VISIBLE_FOR_TESTING_SHOULD_HAVE_CORRECT_VISIBILITY =
      classes()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should()
          .notBePublic()
          .orShould(beAllowedToBePublic())
          .andShould()
          .notBePrivate();

  @ArchTest
  static final ArchRule METHOD_VISIBLE_FOR_TESTING_SHOULD_HAVE_CORRECT_VISIBILITY =
      methods()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should()
          .notBePublic()
          .orShould(beAllowedToBePublic())
          .andShould()
          .notBePrivate();

  @ArchTest
  static final ArchRule FIELD_VISIBLE_FOR_TESTING_SHOULD_HAVE_CORRECT_VISIBILITY =
      fields()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should()
          .notBePublic()
          .orShould(beAllowedToBePublic())
          .andShould()
          .notBePrivate();

  //
  // Conditions implementation
  //

  private static <T extends AndFullName> ArchCondition<T> beAllowedToBePublic() {
    return new ArchCondition<T>("be explicitly allowed to be public") {
      @Override
      public void check(final T element, final ConditionEvents events) {
        if (!PUBLIC_ELEMENTS.contains(element.getFullName())) {
          events.add(
              SimpleConditionEvent.violated(
                  element,
                  "%s is not in the list of allowed public elements"
                      .formatted(displayName(element))));
        }
      }
    };
  }

  private static <T extends AndFullName> ArchCondition<T> beAccessedBySelfOrTest() {
    return new ArchCondition<>("be accessed from test or self") {
      @Override
      public void check(final T element, final ConditionEvents events) {

        final Set<? extends JavaAccess<?>> accessesToSelf;
        final JavaClass owner;
        if (element instanceof final JavaClass javaClass) {
          accessesToSelf = javaClass.getAccessesToSelf();
          owner = javaClass;
        } else if (element instanceof final JavaMember javaMember) {
          accessesToSelf = javaMember.getAccessesToSelf();
          owner = javaMember.getOwner();
        } else {
          return;
        }

        accessesToSelf.stream()
            .filter(access -> isViolation(owner, access.getOriginOwner()))
            .forEach(
                violationAccess ->
                    events.add(
                        SimpleConditionEvent.violated(
                            violationAccess,
                            String.format(
                                "%s is accessed from %s, which is not a test but still requires extended visibility.",
                                displayName(element),
                                violationAccess.getOriginOwner().getFullName()))));
      }
    };
  }

  private static boolean isViolation(final JavaClass javaOwner, final JavaClass javaOriginOwner) {
    return !isCalledBySelf(javaOwner, javaOriginOwner) && !isTestClass(javaOriginOwner);
  }

  private static boolean isTestClass(final JavaClass javaClass) {
    final var javaClassSource = javaClass.getSource();
    return javaClassSource.isPresent() && javaClassSource.get().toString().contains("test");
  }

  private static boolean isCalledBySelf(final JavaClass calledClass, final JavaClass callingClass) {
    // If the calling class is the same as the called class, it's a self-access
    // This also covers the case of nested classes, as they can access private members of their
    // enclosing class and vice versa
    return topLevelClass(callingClass).equals(topLevelClass(calledClass));
  }

  private static JavaClass topLevelClass(final JavaClass javaClass) {
    var topLevelClass = javaClass;
    while (topLevelClass.getEnclosingClass().isPresent()) {
      topLevelClass = topLevelClass.getEnclosingClass().get();
    }
    return topLevelClass;
  }

  private static String displayName(final AndFullName element) {
    return switch (element) {
      case final JavaClass javaClass -> "Class " + element.getFullName();
      case final JavaMethod javaMethod -> "Method " + element.getFullName();
      case final JavaField javaField -> "Field " + element.getFullName();
      default -> element.getFullName();
    };
  }
}
