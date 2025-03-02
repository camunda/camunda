/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

public class ExceptionUtil {

  public static final String ERROR_FAILED_DELETE_REQUEST = "Failed to execute delete request";
  public static final String ERROR_FAILED_FIND_ALL_QUERY = "Failed to execute findAll query";
  public static final String ERROR_FAILED_GET_ALIAS_REQUEST = "Failed to execute getAlias request";
  public static final String ERROR_FAILED_GET_REQUEST = "Failed to execute get request";
  public static final String ERROR_FAILED_INDEX_REQUEST = "Failed to execute index request";
  public static final String ERROR_FAILED_SEARCH_QUERY = "Failed to execute search query";

  public static final String ERROR_NOT_FOUND_AD_HOC_SUBPROCESS =
      "Failed to find Ad-Hoc Subprocess with ID '%s'";
  public static final String ERROR_NOT_FOUND_ENTITY_BY_KEY = "%s with key %s not found";
  public static final String ERROR_NOT_FOUND_FORM_BY_KEY = "Form with formKey %d not found";
  public static final String ERROR_NOT_FOUND_GROUP_BY_KEY = "Group with groupKey %d not found";
  public static final String ERROR_NOT_FOUND_GROUP_BY_NAME = "Group with group name %s not found";
  public static final String ERROR_NOT_FOUND_MAPPING_BY_KEY =
      "Mapping with mappingKey %d not found";
  public static final String ERROR_NOT_FOUND_ROLE_BY_KEY = "Role with roleKey %d not found";
  public static final String ERROR_NOT_FOUND_TENANT = "Tenant matching %s not found";

  public static final String ERROR_NOT_UNIQUE_ENTITY = "Found %s with key %s more than once";
  public static final String ERROR_NOT_UNIQUE_FORM = "Found form with key %d more than once";
  public static final String ERROR_NOT_UNIQUE_TENANT = "Found multiple tenants matching %s";
}
