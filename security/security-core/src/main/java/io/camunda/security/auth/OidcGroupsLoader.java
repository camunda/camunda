/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.cache.CacheProvider;
import com.jayway.jsonpath.spi.cache.LRUCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OidcGroupsLoader {
  public static final String CLAIM_NOT_STRING_OR_STRING_ARRAY =
      "Configured claim for %s (%s) is not a string or string array. Please check your OIDC configuration.";

  private static final Configuration CONFIGURATION =
      Configuration.builder()
          // Ignore the common case that the last path element is not set
          .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
          .jsonProvider(null)
          .mappingProvider(null)
          .build();

  static {
    CacheProvider.setCache(new LRUCache(100));
  }

  private final String groupsClaim;

  public OidcGroupsLoader(final String groupsClaim) {
    this.groupsClaim = groupsClaim;
    if (groupsClaim != null) {
      try {
        JsonPath.compile(groupsClaim);
      } catch (final InvalidPathException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }
  }

  public List<String> load(final Map<String, Object> claims) {
    if (groupsClaim == null) {
      return null;
    }
    final var claimGroups = JsonPath.using(CONFIGURATION).parse(claims).read(groupsClaim);
    final var groups = new ArrayList<String>();
    if (claimGroups != null) {
      if (claimGroups instanceof String) {
        groups.add((String) claimGroups);
      } else if (claimGroups instanceof final List<?> list) {
        for (final Object o : list) {
          if (o instanceof String) {
            groups.add((String) o);
          } else {
            throw new IllegalArgumentException(
                String.format(CLAIM_NOT_STRING_OR_STRING_ARRAY, "groups", groupsClaim));
          }
        }
      } else {
        throw new IllegalArgumentException(
            String.format(CLAIM_NOT_STRING_OR_STRING_ARRAY, "groups", groupsClaim));
      }
    }
    return groups;
  }
}
