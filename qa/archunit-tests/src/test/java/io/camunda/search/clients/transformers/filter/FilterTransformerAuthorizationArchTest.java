/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.security.core.auth.RequiredAuthorization;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Every document-store filter transformer must turn the authorized resource ids into a query.
 *
 * <p>{@code IndexFilterTransformer.toAuthorizationCheckSearchQuery(RequiredAuthorization)} is
 * abstract and only called when authorization is enabled and the caller has resource ids. An
 * override that returns {@code matchAll()} lets every document through regardless of those ids. An
 * override that never reads {@link RequiredAuthorization#resourceIds()} cannot filter by them
 * either. Both are flagged (helpers and lambdas in the same class are followed).
 */
@AnalyzeClasses(
    packages = "io.camunda.search.clients.transformers.filter",
    importOptions = DoNotIncludeTestsOrTestJars.class)
public final class FilterTransformerAuthorizationArchTest {

  // referenced by name: the query transformer module is only a transitive dependency here, and
  // the dependency analysis rejects compile-time use of undeclared dependencies
  static final String INDEX_FILTER_TRANSFORMER =
      "io.camunda.search.clients.transformers.filter.IndexFilterTransformer";
  static final String SEARCH_QUERY_BUILDERS = "io.camunda.search.clients.query.SearchQueryBuilders";

  /**
   * Transformers whose authorization query ignores the authorized resource ids, by simple class
   * name. Each entry needs a comment explaining why. Remove the entry once the transformer filters
   * by the ids; a stale entry fails the rule.
   */
  static final Set<String> ALLOWED_TO_IGNORE_RESOURCE_IDS =
      Set.of(
          // Wildcard-only permissions: the endpoints behind these transformers require a
          // permission that is only ever granted on "*", so a caller has all of it or none.
          // IndexFilterTransformer already returns matchNone() without the grant, so matchAll()
          // here only means "the caller holds the grant on *"; there are no ids to scope by.
          // GET /v2/system/usage-metrics requires SYSTEM:READ_USAGE_METRIC, only granted on *.
          "UsageMetricsFilterTransformer",
          // Same endpoint (GET /v2/system/usage-metrics), TU statistics part.
          "UsageMetricsTUFilterTransformer",
          // GET /v2/jobs/statistics/global requires SYSTEM:READ_JOB_METRIC, only granted on *.
          "GlobalJobStatisticsFilterTransformer",
          // POST /v2/jobs/statistics/by-types requires SYSTEM:READ_JOB_METRIC, only granted on *.
          "JobTypeStatisticsFilterTransformer",
          // POST /v2/jobs/statistics/by-workers requires SYSTEM:READ_JOB_METRIC, only granted on
          // *.
          "JobWorkerStatisticsFilterTransformer",
          // POST /v2/jobs/statistics/time-series requires SYSTEM:READ_JOB_METRIC, only granted on
          // *.
          "JobTimeSeriesStatisticsFilterTransformer",
          // POST /v2/global-task-listeners/search requires GLOBAL_LISTENER:READ_TASK_LISTENER.
          // GLOBAL_LISTENER permissions are only checked against *; a grant on a listener id is
          // never honoured.
          "GlobalListenerFilterTransformer",
          // Forms are not searched by users.
          // Only caller is the start form endpoint (GET /v2/process-definitions/{key}/form).
          // ProcessDefinitionServices checks PROCESS_DEFINITION:READ_PROCESS_DEFINITION with the
          // caller's authentication first; FormServices then looks up that process's form with
          // anonymous authentication, so the checks arrive disabled. Access is decided before.
          "FormFilterTransformer");

  @ArchTest
  static final ArchRule AUTHORIZATION_QUERY_MUST_USE_RESOURCE_IDS =
      ArchRuleDefinition.classes()
          .that()
          .areAssignableTo(INDEX_FILTER_TRANSFORMER)
          .should(buildAuthorizationQueryFromResourceIds())
          .because(
              "a document-store search must only return documents the caller is authorized"
                  + " for; see the Javadoc of FilterTransformerAuthorizationArchTest");

  private static final String AUTHORIZATION_QUERY_METHOD = "toAuthorizationCheckSearchQuery";

  private static ArchCondition<JavaClass> buildAuthorizationQueryFromResourceIds() {
    return new ArchCondition<>("build the authorization query from the authorized resource ids") {

      private final Set<String> usedAllowListEntries = new HashSet<>();

      @Override
      public void check(final JavaClass transformer, final ConditionEvents events) {
        final var method = authorizationQueryMethod(transformer);
        if (method.isEmpty()) {
          // inherits a concrete override from a superclass, which is checked on its own
          return;
        }
        final var calls = reachableCalls(method.get());
        final boolean matchesAll =
            calls.stream()
                .anyMatch(
                    c ->
                        c.getTarget().getOwner().getName().equals(SEARCH_QUERY_BUILDERS)
                            && c.getTarget().getName().equals("matchAll"));
        final boolean readsResourceIds =
            calls.stream()
                .anyMatch(
                    c ->
                        c.getTarget().getOwner().isEquivalentTo(RequiredAuthorization.class)
                            && c.getTarget().getName().equals("resourceIds"));
        if (!matchesAll && readsResourceIds) {
          return;
        }
        final String name = transformer.getSimpleName();
        if (ALLOWED_TO_IGNORE_RESOURCE_IDS.contains(name)) {
          usedAllowListEntries.add(name);
          return;
        }
        final String problem =
            matchesAll
                ? "returns matchAll(), so the authorized resource ids are ignored"
                : "never reads authorization.resourceIds()";
        events.add(
            violated(
                transformer,
                transformer.getName()
                    + "."
                    + AUTHORIZATION_QUERY_METHOD
                    + " "
                    + problem
                    + ". Filter on the field that holds the resource id, e.g."
                    + " stringTerms(BPMN_PROCESS_ID, authorization.resourceIds()) (see"
                    + " ProcessInstanceFilterTransformer). If the resource type is legitimately"
                    + " exempt, add \""
                    + name
                    + "\" with a comment explaining why to ALLOWED_TO_IGNORE_RESOURCE_IDS."));
      }

      @Override
      public void finish(final ConditionEvents events) {
        ALLOWED_TO_IGNORE_RESOURCE_IDS.stream()
            .filter(name -> !usedAllowListEntries.contains(name))
            .sorted()
            .forEach(
                name ->
                    events.add(
                        violated(
                            name,
                            "Allow-list entry \""
                                + name
                                + "\" no longer matches a transformer that ignores the resource"
                                + " ids (fixed or renamed). Remove it from"
                                + " ALLOWED_TO_IGNORE_RESOURCE_IDS.")));
      }
    };
  }

  private static Optional<JavaMethod> authorizationQueryMethod(final JavaClass transformer) {
    return transformer.getMethods().stream()
        .filter(m -> m.getName().equals(AUTHORIZATION_QUERY_METHOD))
        .filter(m -> !m.getModifiers().contains(JavaModifier.BRIDGE))
        .filter(m -> !m.getModifiers().contains(JavaModifier.ABSTRACT))
        .filter(
            m ->
                m.getRawParameterTypes().size() == 1
                    && m.getRawParameterTypes()
                        .getFirst()
                        .isEquivalentTo(RequiredAuthorization.class))
        .findFirst();
  }

  private static Set<JavaMethodCall> reachableCalls(final JavaMethod start) {
    final var owner = start.getOwner();
    final var calls = new HashSet<JavaMethodCall>();
    final var visited = new HashSet<JavaCodeUnit>();
    final var queue = new ArrayDeque<JavaCodeUnit>();
    queue.add(start);
    while (!queue.isEmpty()) {
      final var codeUnit = queue.poll();
      if (!visited.add(codeUnit)) {
        continue;
      }
      for (final JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
        calls.add(call);
        call.getTarget()
            .resolveMember()
            .filter(callee -> callee.getOwner().equals(owner))
            .ifPresent(queue::add);
      }
    }
    return calls;
  }
}
