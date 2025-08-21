/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.commons.utils;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import java.util.regex.Pattern;
import org.opensearch.client.opensearch._types.OpenSearchException;

public class ExceptionFilter {

  public static final Pattern MIGRATION_REPOSITORY_NOT_EXISTS =
      Pattern.compile(
          "no such index \\[[a-zA-Z0-9\\-]+-migration-steps-repository-[0-9]+\\.[0-9]+\\.[0-9]+_]");

  /**
   * Check if the exception should be rethrown or not. Throwing the exception on this stage will
   * cause the Spring Boot application to terminate. Some exceptions can be expected when dealing
   * with Greenfield deployments and these should be ignored.
   *
   * @param exception the exception to check
   * @return true if the exception should be rethrown, false otherwise
   */
  public static boolean shouldThrowException(final Exception exception) {
    if (exception.getCause() instanceof final ElasticsearchException ex) {
      return ex.error().reason() != null
          && !MIGRATION_REPOSITORY_NOT_EXISTS.matcher(ex.error().reason()).find();
    } else if (exception.getCause() instanceof final OpenSearchException ex) {
      return ex.error().reason() != null
          && !MIGRATION_REPOSITORY_NOT_EXISTS.matcher(ex.error().reason()).find();
    }
    return true;
  }
}
