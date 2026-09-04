/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.handlers.MainIndexExporterHandler;
import io.camunda.exporter.handlers.OrdinalIndexExportHandler;
import io.camunda.exporter.handlers.auditlog.AuditLogCleanupHandler;
import io.camunda.exporter.handlers.auditlog.AuditLogHandler;
import io.camunda.exporter.handlers.batchoperation.BatchOperationChunkCreatedItemHandler;
import io.camunda.exporter.handlers.operation.AbstractOperationHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalKeyRelated;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AnalyzeClasses(packages = "io.camunda.exporter", importOptions = DoNotIncludeTestsOrTestJars.class)
public class ExportHandlerArchTest {
  static final Set<Class<?>> RAW_TYPES_THAT_NEED_ORDINAL_HANDLING =
      Set.of(StorageOrdinalKeyRelated.class, OperationEntity.class);
  static final DescribedPredicate<? super JavaClass>
      RAW_TYPES_THAT_NEED_ORDINAL_HANDLING_PREDICATE =
          DescribedPredicate.or(
              RAW_TYPES_THAT_NEED_ORDINAL_HANDLING.stream()
                  .map(Predicates::assignableTo)
                  .toArray(DescribedPredicate[]::new));

  @ArchTest
  static final ArchRule EXPORT_HANDLERS_SHOULD_ONLY_WRITE_TO_ONE_INDEX =
      ArchRuleDefinition.classes()
          .that()
          .areAssignableTo(ExportHandler.class)
          .should(
              new ArchCondition<>("only write to a single index") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  // this is not a perfect verification - essentially we are checking that handlers
                  // only call methods on BatchRequest once. We assume that multiple calls
                  // mean we are trying to write to multiple indexes
                  final List<JavaCall<?>> batchRequestCalls =
                      item.getMethods().stream()
                          .flatMap(method -> method.getCallsFromSelf().stream())
                          .filter(call -> call.getTargetOwner().isAssignableTo(BatchRequest.class))
                          .toList();
                  if (batchRequestCalls.size() > 1) {
                    // we will specifically allow a single delete call combined
                    // with another non-delete call as this is a pattern used in
                    // UserTaskJobBasedHandler which only writes to a single index
                    int deleteCounts = 0;
                    int otherCounts = 0;
                    for (final JavaCall<?> call : batchRequestCalls) {
                      if (call.getTarget().getName().startsWith("delete")) {
                        deleteCounts++;
                      } else {
                        otherCounts++;
                      }
                    }

                    if (deleteCounts == 1 && otherCounts == 1) {
                      return;
                    }

                    final String message =
                        batchRequestCalls.stream()
                            .map(JavaCall::getDescription)
                            .collect(Collectors.joining(" and "));
                    events.add(SimpleConditionEvent.violated(item, message));
                  }
                }
              });

  @ArchTest
  static final ArchRule EXPORT_HANDLERS_SHOULD_NOT_IMPLEMENT_MAIN_AND_ORDINAL_INTERFACES =
      ArchRuleDefinition.classes()
          .that()
          .areAssignableTo(ExportHandler.class)
          .should()
          .notBeAssignableTo(
              DescribedPredicate.and(
                  Predicates.assignableTo(MainIndexExporterHandler.class),
                  Predicates.assignableTo(OrdinalIndexExportHandler.class)));

  @ArchTest
  static final ArchRule EXPORT_HANDLERS_SHOULD_IMPLEMENT_MAIN_OR_ORDINAL_INTERFACES =
      ArchRuleDefinition.classes()
          .that()
          .areAssignableTo(ExportHandler.class)
          .and()
          .doNotHaveModifier(JavaModifier.ABSTRACT)
          .and()
          .areNotAssignableTo(
              DescribedPredicate.or(
                  // audit log handlers have custom handling
                  Predicates.assignableTo(AuditLogHandler.class),
                  Predicates.assignableTo(AuditLogCleanupHandler.class),
                  // operation handler needs to be excluded as it needs slight custom handling
                  Predicates.assignableTo(AbstractOperationHandler.class),
                  // custom handling for batch operation chunk created items (need to look at
                  // ordinal per item)
                  Predicates.assignableTo(BatchOperationChunkCreatedItemHandler.class)))
          // .and()
          // TODO remove these exclusions once we have refactored the handlers to implement the
          // correct interface
          // .resideOutsideOfPackages("io.camunda.exporter.handlers.batchoperation..")
          .should()
          .beAssignableTo(
              DescribedPredicate.or(
                  Predicates.assignableTo(MainIndexExporterHandler.class),
                  Predicates.assignableTo(OrdinalIndexExportHandler.class)));

  @ArchTest
  static final ArchRule
      EXPORT_HANDLERS_SHOULD_NOT_IMPLEMENT_MAIN_FOR_STORAGE_ORDINAL_KEY_RELATED_RECORDS_AND_ENTITIES =
          ArchRuleDefinition.classes()
              .that()
              .areAssignableTo(MainIndexExporterHandler.class)
              .and()
              .doNotHaveModifier(JavaModifier.ABSTRACT)
              .should(
                  new ArchCondition<>(
                      "not target main indexes when using raw types: "
                          + RAW_TYPES_THAT_NEED_ORDINAL_HANDLING) {
                    @Override
                    public void check(final JavaClass item, final ConditionEvents events) {
                      final var violatingRawTypes = new LinkedHashSet<JavaClass>();
                      for (final var clazz : item.getClassHierarchy()) {
                        for (final var interf : clazz.getInterfaces()) {
                          for (final var rawType : interf.getAllInvolvedRawTypes()) {
                            if (rawType.isAssignableTo(
                                RAW_TYPES_THAT_NEED_ORDINAL_HANDLING_PREDICATE)) {
                              violatingRawTypes.add(rawType);
                            }
                          }
                        }
                      }
                      for (final var rawType : violatingRawTypes) {
                        events.add(
                            SimpleConditionEvent.violated(
                                item,
                                "Class "
                                    + item.getName()
                                    + " implements "
                                    + MainIndexExporterHandler.class.getSimpleName()
                                    + " but uses raw type ("
                                    + rawType.getName()
                                    + ") that implements one of:"
                                    + RAW_TYPES_THAT_NEED_ORDINAL_HANDLING));
                      }
                    }
                  });
}
