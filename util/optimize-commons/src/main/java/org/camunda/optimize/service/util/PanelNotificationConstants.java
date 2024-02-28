/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PanelNotificationConstants {
  // API constants
  public static final String SEND_NOTIFICATION_TO_ALL_ORG_USERS_ENDPOINT = "/notifications/org";

  // Request constants
  public static final String OPTIMIZE_SOURCE = "optimize";
  public static final String ORG_TYPE = "org";

  // Notification content
  public static final String INITIAL_VISIT_TO_INSTANT_DASHBOARD_ID =
      "initialVisitToInstantDashboard";
  public static final String INITIAL_VISIT_TO_INSTANT_DASHBOARD_TITLE =
      "See how your process is doing";
  public static final String INITIAL_VISIT_TO_INSTANT_DASHBOARD_CONTENT =
      "Your first process of %s was started successfully. Track the status in the instant preview dashboard.";
  public static final String INITIAL_VISIT_TO_INSTANT_DASHBOARD_LINK_LABEL =
      "View instant preview dashboard";
  public static final String[] OPTIMIZE_USER_PERMISSIONS = new String[] {"cluster:optimize:read"};
}
