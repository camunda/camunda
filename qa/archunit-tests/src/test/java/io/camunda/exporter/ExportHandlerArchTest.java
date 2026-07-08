/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import java.util.List;
import java.util.stream.Collectors;

@AnalyzeClasses(packages = "io.camunda.exporter", importOptions = DoNotIncludeTestsOrTestJars.class)
public class ExportHandlerArchTest {
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
}
