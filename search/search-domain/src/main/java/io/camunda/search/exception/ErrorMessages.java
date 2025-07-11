/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.exception;

public class ErrorMessages {

  public static final String ERROR_ENTITY_BY_KEY_NOT_FOUND = "%s with key '%s' not found";

  public static final String ERROR_RESOURCE_ACCESS_CHECKS_NOT_DEFINED =
      "Failed to execute search query, resource access checks are not defined";
  public static final String ERROR_RESOURCE_ACCESS_CHECKS_AUTHORIZATION_NOT_DEFINED =
      "Failed to execute search query, authorization checks are not defined";
  public static final String ERROR_RESOURCE_ACCESS_CHECKS_TENANT_NOT_DEFINED =
      "Failed to execute search query, tenant checks are not defined";

  public static final String ERROR_FAILED_DELETE_REQUEST = "Failed to execute delete request";
  public static final String ERROR_FAILED_FIND_ALL_QUERY = "Failed to execute findAll query";
  public static final String ERROR_FAILED_GET_ALIAS_REQUEST = "Failed to execute getAlias request";
  public static final String ERROR_FAILED_GET_REQUEST = "Failed to execute get request";
  public static final String ERROR_FAILED_INDEX_REQUEST = "Failed to execute index request";
  public static final String ERROR_FAILED_SEARCH_QUERY = "Failed to execute search query";
  public static final String ERROR_FAILED_AGGREGATE_QUERY = "Failed to execute aggregate query";

  public static final String ERROR_NOT_FOUND_AD_HOC_SUB_PROCESS =
      "Failed to find ad-hoc sub-process with ID '%s'";
  public static final String ERROR_SINGLE_RESULT_NOT_UNIQUE =
      "A single result was expected, but multiple results were found matching %s";
  public static final String ERROR_SINGLE_RESULT_NOT_FOUND =
      "A single result was expected, but none was found matching %s";

  public static final String ERROR_GET_BY_QUERY_NOT_SUPPORTED_RESULT_TYPE =
      "Result type %s is not supported for getByQuery()";
  public static final String ERROR_GET_BY_QUERY_NOT_UNIQUE =
      "Failed to get entity by a search request, the search query returned more than one result matching %s";

  public static final String ERROR_RESOURCE_ACCESS_CONTROLLER_NOT_FOUND =
      "No resource access controller found";
}
