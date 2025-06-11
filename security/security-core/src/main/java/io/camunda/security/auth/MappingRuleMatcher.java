/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches mapping rules against claims by evaluating JSONPath expressions for each mapping rule.
 * Keeps a cache of compiled JSONPath expressions to avoid recompiling the same expressions multiple
 * times.
 */
public final class MappingRuleMatcher {
  private static final Configuration CONFIGURATION =
      Configuration.builder()
          // Ignore the common case that the last path element is not set
          .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
          .jsonProvider(null)
          .mappingProvider(null)
          .build();
  private static final Logger LOG = LoggerFactory.getLogger(MappingRuleMatcher.class);

  private MappingRuleMatcher() {}

  public static <T extends MappingRule> Stream<T> matchingRules(
      final Stream<T> mappingRules, final Map<String, Object> claims) {
    final EvaluationCache evaluationCache = new EvaluationCache(claims);
    return mappingRules.filter(mappingRule -> matchRule(evaluationCache, mappingRule));
  }

  private static boolean matchRule(
      final EvaluationCache evaluationCache, final MappingRule mappingRule) {
    final Object claimValue;
    try {
      claimValue = evaluationCache.evaluate(mappingRule.claimName());
    } catch (final JsonPathException e) {
      return false;
    }
    if (claimValue instanceof final Collection<?> claimValues) {
      return claimValues.contains(mappingRule.claimValue());
    }
    return mappingRule.claimValue().equals(claimValue);
  }

  /**
   * A short-lived cache for evaluating many expressions against the same claims. Results are cached
   * so each expression is only evaluated once.
   */
  private static final class EvaluationCache {
    private final Map<String, Object> claims;
    private final Map<String, Object> evaluations = new HashMap<>();

    public EvaluationCache(final Map<String, Object> claims) {
      this.claims = claims;
    }

    public Object evaluate(final String expression) {
      return evaluations.computeIfAbsent(
          expression, exp -> JsonPath.compile(exp).read(claims, CONFIGURATION));
    }
  }

  public interface MappingRule {
    String mappingId();

    String claimName();

    String claimValue();
  }
}
