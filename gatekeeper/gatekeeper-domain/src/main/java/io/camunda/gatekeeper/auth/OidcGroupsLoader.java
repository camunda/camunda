/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.auth;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OidcGroupsLoader {

  public static final String DERIVED_GROUPS_ARE_NOT_STRING_ARRAY =
      "Group's list derived from (%s) is not a string array."
          + " Please check your OIDC configuration.";

  private static final Logger LOG = LoggerFactory.getLogger(OidcGroupsLoader.class);
  private static final Configuration CONFIGURATION =
      Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();

  private final String groupsClaim;
  private final JsonPath compiledPath;
  private final ReentrantLock evaluationLock = new ReentrantLock();

  public OidcGroupsLoader(final String groupsClaim) {
    if (groupsClaim != null && !groupsClaim.isEmpty()) {
      this.groupsClaim = sanitizeClaimPath(groupsClaim);
      try {
        compiledPath = JsonPath.compile(this.groupsClaim);
      } catch (final InvalidPathException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    } else {
      this.groupsClaim = null;
      compiledPath = null;
    }
  }

  public List<String> load(final Map<String, Object> claims) {
    if (groupsClaim == null) {
      return List.of();
    }
    evaluationLock.lock();
    try {
      return doLoad(claims);
    } finally {
      evaluationLock.unlock();
    }
  }

  public String getGroupsClaim() {
    return groupsClaim;
  }

  private List<String> doLoad(final Map<String, Object> claims) {
    final var groups = new ArrayList<String>();
    try {
      final var claimGroups =
          JsonPath.using(CONFIGURATION).parse(claims).read(compiledPath.getPath());
      if (claimGroups != null) {
        if (claimGroups instanceof final String group) {
          groups.add(group);
        } else if (claimGroups instanceof final List<?> list) {
          for (final Object o : list) {
            if (o instanceof final String group) {
              groups.add(group);
            } else {
              throw new IllegalArgumentException(
                  String.format(DERIVED_GROUPS_ARE_NOT_STRING_ARRAY, groupsClaim));
            }
          }
        } else {
          throw new IllegalArgumentException(
              String.format(DERIVED_GROUPS_ARE_NOT_STRING_ARRAY, groupsClaim));
        }
      }
    } catch (final PathNotFoundException exception) {
      LOG.warn("Could not load groups from claims {}", claims, exception);
    }
    return groups;
  }

  private String sanitizeClaimPath(final String claim) {
    return claim.startsWith("$") ? claim : "$['" + claim + "']";
  }
}
