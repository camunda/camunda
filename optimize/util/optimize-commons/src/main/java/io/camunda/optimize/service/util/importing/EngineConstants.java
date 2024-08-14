/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.importing;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EngineConstants {

  public static final String MAX_RESULTS_TO_RETURN = "maxResults";
  public static final String INDEX_OF_FIRST_RESULT = "firstResult";

  public static final String SORT_BY = "sortBy";
  public static final String SORT_ORDER = "sortOrder";
  public static final String SORT_ORDER_ASC = "asc";

  public static final String MEMBER = "member";
  public static final String MEMBER_OF_GROUP = "memberOfGroup";

  public static final String USER_ID_IN = "userIdIn";
  public static final String GROUP_ID_IN = "groupIdIn";

  public static final String VERSION_ENDPOINT = "/version";
  public static final String USER_VALIDATION_ENDPOINT = "/identity/verify";
  public static final String AUTHORIZATION_ENDPOINT = "/authorization";
  public static final String GROUP_ENDPOINT = "/group";
  public static final String GROUP_BY_ID_ENDPOINT_TEMPLATE = "/group/{id}";
  public static final String USER_ENDPOINT = "/user";
  public static final String USER_BY_ID_ENDPOINT_TEMPLATE = "/user/{id}/profile";
  public static final String USER_COUNT_ENDPOINT = "/user/count";

  public static final String ALL_PERMISSION = "ALL";
  public static final String ACCESS_PERMISSION = "ACCESS";
  public static final String READ_HISTORY_PERMISSION = "READ_HISTORY";
  public static final String READ_PERMISSION = "READ";

  public static final String RESOURCE_TYPE = "resourceType";
  public static final int RESOURCE_TYPE_APPLICATION = 0;
  public static final int RESOURCE_TYPE_PROCESS_DEFINITION = 6;
  public static final int RESOURCE_TYPE_DECISION_DEFINITION = 10;
  public static final int RESOURCE_TYPE_USER = 1;
  public static final int RESOURCE_TYPE_GROUP = 2;
  public static final int RESOURCE_TYPE_TENANT = 11;

  public static final int AUTHORIZATION_TYPE_GLOBAL = 0;
  public static final int AUTHORIZATION_TYPE_GRANT = 1;
  public static final int AUTHORIZATION_TYPE_REVOKE = 2;

  public static final String OPTIMIZE_APPLICATION_RESOURCE_ID = "optimize";
  public static final String ALL_RESOURCES_RESOURCE_ID = "*";
}
