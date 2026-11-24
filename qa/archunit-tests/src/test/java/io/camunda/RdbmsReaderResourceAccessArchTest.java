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
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.security.core.authz.AuthorizationCheck;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * RDBMS reader searches and aggregations must apply the resource part of the {@link
 * ResourceAccessChecks} they receive, not only the tenant part.
 *
 * <p>The service layer computes the authorized resource ids and hands them down; nothing else makes
 * the storage layer use them. Two rules, each with its own allow-list:
 *
 * <ol>
 *   <li>{@link #READER_SEARCHES_MUST_CHECK_RESOURCE_ACCESS}: the method returns early when the
 *       caller has no resource access, by calling {@code
 *       AbstractEntityReader.shouldReturnEmptyResult} or {@code noResourceAccess}, or {@code
 *       AuthorizationCheck.hasAnyResourceAccess} directly.
 *   <li>{@link #READER_SEARCHES_MUST_SCOPE_BY_RESOURCE_IDS}: the method passes the authorized
 *       resource ids on to the query, by reading {@link
 *       ResourceAccessChecks#getAuthorizedResourceIdsByType()} or by handing {@link
 *       ResourceAccessChecks#authorizationCheck()} to a query type in {@code
 *       io.camunda.db.rdbms.read.domain}. Resource types that only accept the wildcard {@code *}
 *       have nothing to scope by; they go on the allow-list.
 * </ol>
 *
 * <p>Calls are followed into the reader's own methods (helpers, overloads, lambdas). For the first
 * rule they are also followed into static methods of other readers, so a reader may reuse another
 * reader's guard. A same-named helper (for example a local {@code shouldReturnEmptyResult} that
 * checks tenants only) does not count by its name: it must itself meet the rule.
 *
 * <p>Lookups by identifier (every parameter other than the {@link ResourceAccessChecks} is a scalar
 * such as a key, an id or a name) are out of scope: {@code AbstractResourceAccessController} checks
 * the fetched entity after the read.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.read.service",
    importOptions = DoNotIncludeTestsOrTestJars.class)
public final class RdbmsReaderResourceAccessArchTest {

  /**
   * Allow-list of {@link #READER_SEARCHES_MUST_CHECK_RESOURCE_ACCESS}: the "no grant, no data"
   * guard.
   *
   * <p>The rule requires a reader to return an empty result, without querying, when the caller has
   * no resource access at all. Methods listed here are exempt and may query anyway, so for them the
   * guarantee rests elsewhere (or is missing). This is independent of {@link
   * #ALLOWED_WITHOUT_RESOURCE_ID_SCOPING}: a reader can return early correctly and still not limit
   * its query to the granted ids, or the other way round.
   *
   * <p>Entries are {@code SimpleClassName#method}, each with a comment explaining why. Remove an
   * entry once the method checks; a stale entry fails the rule.
   */
  static final Set<String> ALLOWED_WITHOUT_RESOURCE_ACCESS_CHECK =
      Set.of(
          // Readers that may query even when the caller has no grant at all.
          // Stub that only throws, so there is nothing to check yet.
          // TODO https://github.com/camunda/pdp-3517-variable-trace/issues/189:
          // pending to remove once the feature is complete.
          "VariableHistoryDbReader#search",
          // Only caller is the start form endpoint (GET /v2/process-definitions/{key}/form).
          // ProcessDefinitionServices checks PROCESS_DEFINITION:READ_PROCESS_DEFINITION with the
          // caller's authentication first; FormServices then looks up that process's form with
          // anonymous authentication, so the checks arrive disabled. Access is decided before.
          "FormDbReader#search");

  /**
   * Allow-list of {@link #READER_SEARCHES_MUST_SCOPE_BY_RESOURCE_IDS}: the "a grant on A does not
   * show B" guard.
   *
   * <p>The rule requires a reader to pass the authorized resource ids on to the query, so a caller
   * granted access to some resources only sees those. Methods listed here are exempt, mostly
   * because their resource type only accepts the wildcard {@code *}: a grant is all or nothing, so
   * there is nothing to scope by, and {@link #READER_SEARCHES_MUST_CHECK_RESOURCE_ACCESS} is what
   * protects them. A method can appear in both lists, in one, or in neither.
   *
   * <p>Entries are {@code SimpleClassName#method}, each with a comment explaining why. Remove an
   * entry once the method scopes by the ids; a stale entry fails the rule.
   */
  static final Set<String> ALLOWED_WITHOUT_RESOURCE_ID_SCOPING =
      Set.of(
          // Readers that may query without limiting the result to the granted resource ids.
          // Stub that only throws, so there is nothing to check yet.
          // TODO https://github.com/camunda/pdp-3517-variable-trace/issues/189:
          // pending to remove once the feature is complete.
          "VariableHistoryDbReader#search",
          // Only caller is the start form endpoint (GET /v2/process-definitions/{key}/form).
          // ProcessDefinitionServices checks PROCESS_DEFINITION:READ_PROCESS_DEFINITION with the
          // caller's authentication first; FormServices then looks up that process's form with
          // anonymous authentication, so the checks arrive disabled. Access is decided before.
          "FormDbReader#search",
          // Wildcard-only permissions: the endpoints behind these readers require a permission
          // that is only ever granted on "*", so a caller has all of it or none. The readers
          // still return empty without the grant (READER_SEARCHES_MUST_CHECK_RESOURCE_ACCESS) and
          // filter by tenant where the data has one; there are just no resource ids to scope by.
          // GET /v2/system/usage-metrics requires SYSTEM:READ_USAGE_METRIC, only granted on *.
          "UsageMetricsDbReader#usageMetricStatistics",
          // Same endpoint (GET /v2/system/usage-metrics), TU statistics part.
          "UsageMetricTUDbReader#usageMetricTUStatistics",
          // GET /v2/jobs/statistics/global requires SYSTEM:READ_JOB_METRIC, only granted on *.
          "JobMetricsBatchDbReader#getGlobalJobStatistics",
          // POST /v2/jobs/statistics/by-types requires SYSTEM:READ_JOB_METRIC, only granted on *.
          "JobMetricsBatchDbReader#getJobTypeStatistics",
          // POST /v2/jobs/statistics/by-workers requires SYSTEM:READ_JOB_METRIC, only granted on
          // *.
          "JobMetricsBatchDbReader#getJobWorkerStatistics",
          // POST /v2/jobs/statistics/time-series requires SYSTEM:READ_JOB_METRIC, only granted on
          // *.
          "JobMetricsBatchDbReader#getJobTimeSeriesStatistics",
          // POST /v2/global-task-listeners/search requires GLOBAL_LISTENER:READ_TASK_LISTENER.
          // GLOBAL_LISTENER permissions are only checked against *; a grant on a listener id is
          // never honoured.
          "GlobalListenerDbReader#search");

  @ArchTest
  static final ArchRule READER_SEARCHES_MUST_CHECK_RESOURCE_ACCESS =
      readers()
          .should(
              everySearchMeets(
                  "return early without resource access",
                  call -> isAbstractResourceCheckHelper(call) || isInlineResourceAccessCheck(call),
                  true,
                  ALLOWED_WITHOUT_RESOURCE_ACCESS_CHECK,
                  "ALLOWED_WITHOUT_RESOURCE_ACCESS_CHECK",
                  "never checks resource access. Return an empty result when"
                      + " shouldReturnEmptyResult(resourceAccessChecks) is true (see"
                      + " ProcessInstanceDbReader#search)"))
          .because("the storage layer must apply the resource access checks it receives");

  @ArchTest
  static final ArchRule READER_SEARCHES_MUST_SCOPE_BY_RESOURCE_IDS =
      readers()
          .should(
              everySearchMeets(
                  "pass the authorized resource ids on to the query",
                  call -> readsAuthorizedResourceIds(call) || handsOnAuthorizationCheck(call),
                  false,
                  ALLOWED_WITHOUT_RESOURCE_ID_SCOPING,
                  "ALLOWED_WITHOUT_RESOURCE_ID_SCOPING",
                  "never passes the authorized resource ids to the query. Read"
                      + " resourceAccessChecks.getAuthorizedResourceIdsByType() and filter on it"
                      + " in the mapper (see ProcessInstanceDbReader#search), or hand"
                      + " resourceAccessChecks.authorizationCheck() to a query type in"
                      + " io.camunda.db.rdbms.read.domain (see AuditLogDbReader#search). A"
                      + " resource type that is only granted on * is exempt"))
          .because("a grant on some resources must not reveal the others");

  private static final String ABSTRACT_ENTITY_READER =
      "io.camunda.db.rdbms.read.service.AbstractEntityReader";
  private static final String READ_DOMAIN_PACKAGE = "io.camunda.db.rdbms.read.domain";
  private static final Set<String> RESOURCE_CHECK_HELPERS =
      Set.of("shouldReturnEmptyResult", "noResourceAccess");

  private static GivenClassesConjunction readers() {
    return ArchRuleDefinition.classes()
        .that()
        .haveSimpleNameEndingWith("DbReader")
        .and()
        .areNotInterfaces();
  }

  private static ArchCondition<JavaClass> everySearchMeets(
      final String description,
      final Predicate<JavaMethodCall> satisfyingCall,
      final boolean followOtherReadersStaticHelpers,
      final Set<String> allowList,
      final String allowListName,
      final String problem) {
    return new ArchCondition<>(description + " in every search taking ResourceAccessChecks") {

      private final Set<String> usedAllowListEntries = new HashSet<>();

      @Override
      public void check(final JavaClass reader, final ConditionEvents events) {
        for (final JavaMethod method : reader.getMethods()) {
          if (!isSearchTakingResourceAccessChecks(method)
              || reachesCall(method, satisfyingCall, followOtherReadersStaticHelpers)) {
            continue;
          }
          final String id = reader.getSimpleName() + "#" + method.getName();
          if (allowList.contains(id)) {
            usedAllowListEntries.add(id);
            continue;
          }
          events.add(
              violated(
                  method,
                  method.getFullName()
                      + " takes ResourceAccessChecks but "
                      + problem
                      + ". If the method is legitimately exempt, add \""
                      + id
                      + "\" with a comment explaining why to "
                      + allowListName
                      + "."));
        }
      }

      @Override
      public void finish(final ConditionEvents events) {
        allowList.stream()
            .filter(id -> !usedAllowListEntries.contains(id))
            .sorted()
            .forEach(
                id ->
                    events.add(
                        violated(
                            id,
                            "Allow-list entry \""
                                + id
                                + "\" no longer matches a method that breaks the rule (fixed or"
                                + " renamed). Remove it from "
                                + allowListName
                                + ".")));
      }
    };
  }

  private static boolean isSearchTakingResourceAccessChecks(final JavaMethod method) {
    if (!method.getModifiers().contains(JavaModifier.PUBLIC)
        || method.getModifiers().contains(JavaModifier.BRIDGE)
        || method.getModifiers().contains(JavaModifier.SYNTHETIC)) {
      return false;
    }
    final var parameterTypes = method.getRawParameterTypes();
    final boolean takesResourceAccessChecks =
        parameterTypes.stream().anyMatch(t -> t.isEquivalentTo(ResourceAccessChecks.class));
    // a lookup by identifier has only scalar parameters besides the access checks; it is
    // post-filtered by AbstractResourceAccessController, so only query-shaped methods are checked
    final boolean takesQueryObject =
        parameterTypes.stream()
            .filter(t -> !t.isEquivalentTo(ResourceAccessChecks.class))
            .anyMatch(t -> !isScalar(t));
    return takesResourceAccessChecks && takesQueryObject;
  }

  private static boolean isScalar(final JavaClass type) {
    return type.isPrimitive()
        || type.isEnum()
        || type.getPackageName().equals("java.lang")
        || type.isEquivalentTo(UUID.class);
  }

  /** An inline guard, e.g. a reader's own static helper that mixes the checks differently. */
  private static boolean isInlineResourceAccessCheck(final JavaMethodCall call) {
    return call.getTarget().getOwner().isEquivalentTo(AuthorizationCheck.class)
        && call.getTarget().getName().equals("hasAnyResourceAccess");
  }

  private static boolean readsAuthorizedResourceIds(final JavaMethodCall call) {
    return call.getTarget().getOwner().isEquivalentTo(ResourceAccessChecks.class)
        && call.getTarget().getName().equals("getAuthorizedResourceIdsByType");
  }

  /**
   * Hands the whole authorization to a query type, e.g. {@code
   * AuditLogAuthorizationFilter.of(resourceAccessChecks.authorizationCheck())}. Only calls into
   * {@code io.camunda.db.rdbms.read.domain} count, so passing it to a logging helper or another
   * reader does not.
   */
  private static boolean handsOnAuthorizationCheck(final JavaMethodCall call) {
    return call.getTarget().getOwner().getPackageName().equals(READ_DOMAIN_PACKAGE)
        && call.getTarget().getRawParameterTypes().stream()
            .anyMatch(t -> t.isEquivalentTo(AuthorizationCheck.class));
  }

  private static boolean isStaticReaderMethod(final JavaCodeUnit codeUnit) {
    return codeUnit.getModifiers().contains(JavaModifier.STATIC)
        && codeUnit.getOwner().getSimpleName().endsWith("DbReader");
  }

  private static boolean isAbstractResourceCheckHelper(final JavaMethodCall call) {
    return call.getTarget()
        .resolveMember()
        .filter(callee -> callee.getOwner().getName().equals(ABSTRACT_ENTITY_READER))
        .filter(callee -> RESOURCE_CHECK_HELPERS.contains(callee.getName()))
        .isPresent();
  }

  /**
   * Searches the calls of {@code start} and of the reader's own methods it reaches, and optionally
   * of static methods of other readers.
   */
  private static boolean reachesCall(
      final JavaMethod start,
      final Predicate<JavaMethodCall> satisfyingCall,
      final boolean followOtherReadersStaticHelpers) {
    final var owner = start.getOwner();
    final var visited = new HashSet<JavaCodeUnit>();
    final var queue = new ArrayDeque<JavaCodeUnit>();
    queue.add(start);
    while (!queue.isEmpty()) {
      final var codeUnit = queue.poll();
      if (!visited.add(codeUnit)) {
        continue;
      }
      for (final JavaMethodCall call : codeUnit.getMethodCallsFromSelf()) {
        if (satisfyingCall.test(call)) {
          return true;
        }
        call.getTarget()
            .resolveMember()
            .filter(
                callee ->
                    callee.getOwner().equals(owner)
                        || (followOtherReadersStaticHelpers && isStaticReaderMethod(callee)))
            .ifPresent(queue::add);
      }
    }
    return false;
  }
}
