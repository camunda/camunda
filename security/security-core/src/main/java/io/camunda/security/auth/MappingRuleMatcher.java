/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.jcip.annotations.NotThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    return matchingRules(new CompilationCache(), mappingRules, claims);
  }

  public static <T extends MappingRule> Stream<T> matchingRules(
      final CompilationCache compilationCache,
      final Stream<T> mappingRules,
      final Map<String, Object> claims) {
    final EvaluationCache evaluationCache = new EvaluationCache(compilationCache, claims);
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
   * A long-lived cache for compiling expressions. This is used to avoid recompiling the same
   * expression multiple times.
   *
   * <p>This cache is not thread-safe, mostly because <a
   * href="https://github.com/json-path/JsonPath/issues/975">JsonPath is not thread safe</a>.
   */
  @NotThreadSafe
  public static final class CompilationCache {
    private final Cache<String, JsonPath> cache = Caffeine.newBuilder().maximumSize(1024).build();

    public JsonPath compile(final String expression) {
      try {
        return cache.get(expression, JsonPath::compile);
      } catch (final RuntimeException e) {
        LOG.warn("Failed to compile expression {}", expression, e);
        return null;
      }
    }
  }

  /**
   * A short-lived cache for evaluating many expressions against the same claims. Results are cached
   * so each expression is only evaluated once.
   *
   * <p>This cache is not thread-safe because the underlying {@link CompilationCache} is not
   * thread-safe either.
   */
  @NotThreadSafe
  private static final class EvaluationCache {
    private final CompilationCache compilationCache;
    private final Map<String, Object> claims;
    private final Map<String, Object> evaluations = new HashMap<>();

    public EvaluationCache(
        final CompilationCache compilationCache, final Map<String, Object> claims) {
      this.compilationCache = compilationCache;
      this.claims = claims;
    }

    public Object evaluate(final String expression) {
      return evaluations.computeIfAbsent(
          expression,
          exp -> {
            final var compiledExpression = compilationCache.compile(exp);
            if (compiledExpression == null) {
              // If the expression could not be compiled, we can't evaluate it either.
              return null;
            }
            return compiledExpression.read(claims, CONFIGURATION);
          });
    }
  }

  public interface MappingRule {
    String mappingId();

    String claimName();

    String claimValue();
  }
}
