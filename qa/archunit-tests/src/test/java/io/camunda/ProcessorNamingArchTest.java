/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.*;

@AnalyzeClasses(
    packages = "io.camunda.zeebe.engine",
    importOptions = ImportOption.DoNotIncludeTests.class)
public final class ProcessorNamingArchTest {

  @ArchTest
  static final ArchRule REQUIRE_PROCESSORS_TO_FOLLOW_NAMING_CONVENTION =
      ArchRuleDefinition.classes()
          .that()
          .implement(TypedRecordProcessor.class)
          .should(
              new ArchCondition<>(
                  "follow the processor naming convention: <ValueType><Intent>Processor") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  // The naming convention is that all processors must be named
                  // <ValueType><Intent>Processor
                  // where:
                  // - <ValueType> is the name of the value type being processed (from the
                  // io.camunda.zeebe.protocol.record.ValueType enum)
                  // - <Intent> is the name of the intent being processed (from the
                  // io.camunda.zeebe.protocol.record.intent.<ValueType>Intent enum)
                  // - "Processor" is the suffix indicating that this class is a processor

                  NamingCache.ensureInitialized();

                  final String simpleName = item.getSimpleName();

                  if (NamingCache.WHITELIST.contains(simpleName)) {
                    return; // explicitly allowed exceptions
                  }

                  if (!simpleName.endsWith("Processor")) {
                    events.add(
                        violated(
                            item,
                            "Processor class '"
                                + simpleName
                                + "' must end with suffix 'Processor'."));
                    return;
                  }

                  final String coreName =
                      simpleName.substring(0, simpleName.length() - "Processor".length());
                  if (coreName.isEmpty()) {
                    events.add(
                        violated(
                            item,
                            "Processor class '"
                                + simpleName
                                + "' must start with a value type prefix."));
                    return;
                  }

                  final String matchedValueType = NamingCache.findMatchingValueType(coreName);
                  if (matchedValueType == null) {
                    events.add(
                        violated(
                            item,
                            "Processor class '"
                                + simpleName
                                + "' must start with a valid value type (one of: "
                                + String.join(", ", NamingCache.VALUE_TYPES)
                                + ")."));
                    return;
                  }

                  final String intentPart = coreName.substring(matchedValueType.length());
                  if (intentPart.isEmpty()) {
                    events.add(
                        violated(
                            item,
                            "Processor class '"
                                + simpleName
                                + "' should specify an intent (e.g. '"
                                + matchedValueType
                                + "<Intent>Processor') or be added to the whitelist if it intentionally handles multiple intents."));
                    return;
                  }

                  final Set<String> intents = NamingCache.intentsFor(matchedValueType);
                  if (intents.isEmpty()) {
                    // No intents known for value type; cannot validate intent portion strictly
                    events.add(
                        violated(
                            item,
                            "Processor class '"
                                + simpleName
                                + "' uses value type '"
                                + matchedValueType
                                + "' which has no discoverable intents; expected '<ValueType><Intent>Processor'."));
                    return;
                  }

                  if (!intents.contains(intentPart)) {
                    events.add(
                        violated(
                            item,
                            "Processor class '"
                                + simpleName
                                + "' has unknown intent '"
                                + intentPart
                                + "' for value type '"
                                + matchedValueType
                                + "'. Known intents: "
                                + String.join(", ", intents)
                                + "."));
                  }
                }

                // cache holder to avoid rebuilding reflection data for every class
                private static final class NamingCache {
                  private static final Map<String, Set<String>> VALUE_TYPE_TO_INTENTS =
                      new HashMap<>();
                  private static final Set<String> VALUE_TYPES = new HashSet<>();
                  private static final Set<String> WHITELIST =
                      Set.of(
                          "BpmnStreamProcessor", // special case
                          "UserTaskProcessor"); // will be refactored in the future
                  private static volatile boolean initialized = false;

                  private static void ensureInitialized() {
                    if (initialized) {
                      return;
                    }
                    synchronized (NamingCache.class) {
                      if (initialized) {
                        return;
                      }
                      for (final ValueType constant : ValueType.values()) {
                        final String enumName = constant.name();
                        final String pascal = toPascal(enumName);
                        VALUE_TYPES.add(pascal);
                        // attempt to load corresponding Intent enum
                        final Class<?> intentEnumClass =
                            Intent.VALUE_TYPE_TO_INTENT_MAP.get(constant);
                        if (intentEnumClass != null) {
                          final Object[] intentConstants = intentEnumClass.getEnumConstants();
                          final Set<String> intents = new HashSet<>();
                          if (intentConstants != null) {
                            for (final Object intent : intentConstants) {
                              final String intentPascal = toPascal(((Enum<?>) intent).name());
                              intents.add(intentPascal);
                            }
                          }
                          VALUE_TYPE_TO_INTENTS.put(pascal, intents);
                        } else {
                          // No intent enum available; leave empty set (some ValueTypes may be meta)
                          VALUE_TYPE_TO_INTENTS.put(pascal, Collections.emptySet());
                        }
                      }
                      initialized = true;
                    }
                  }

                  private static String toPascal(final String enumName) {
                    final String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
                    final StringBuilder sb = new StringBuilder();
                    for (final String p : parts) {
                      if (p.isEmpty()) {
                        continue;
                      }
                      sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
                    }
                    return sb.toString();
                  }

                  private static String findMatchingValueType(final String coreName) {
                    // longest prefix match to avoid accidental short matches
                    String match = null;
                    for (final String vt : VALUE_TYPES) {
                      if (coreName.startsWith(vt)
                          && (match == null || vt.length() > match.length())) {
                        match = vt;
                      }
                    }
                    return match;
                  }

                  private static Set<String> intentsFor(final String valueTypePascal) {
                    return VALUE_TYPE_TO_INTENTS.getOrDefault(
                        valueTypePascal, Collections.emptySet());
                  }
                }
              });
}
