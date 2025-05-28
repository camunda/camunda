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
import java.util.Map;
import java.util.stream.Stream;
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
    return mappingRules.filter(mappingRule -> matchRule(mappingRule, claims));
  }

  private static boolean matchRule(
      final MappingRule mappingRule, final Map<String, Object> claims) {
    final JsonPath compiledPath;
    try {
      compiledPath = JsonPath.compile(mappingRule.claimName());
    } catch (final JsonPathException e) {
      LOG.warn(
          "Failed to compile expression {} for mapping rule {}",
          mappingRule.claimName(),
          mappingRule.mappingId(),
          e);
      return false;
    }

    final Object claimValue;
    try {
      claimValue = compiledPath.read(claims, CONFIGURATION);
    } catch (final JsonPathException e) {
      return false;
    }
    if (claimValue instanceof final Collection<?> claimValues) {
      return claimValues.contains(mappingRule.claimValue());
    }
    return mappingRule.claimValue().equals(claimValue);
  }

  public interface MappingRule {
    String mappingId();

    String claimName();

    String claimValue();
  }
}
