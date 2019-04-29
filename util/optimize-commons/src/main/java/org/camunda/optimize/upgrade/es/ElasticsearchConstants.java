/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

public class ElasticsearchConstants {

  // Note: we cap listings to 1000 as a generous practical limit, no paging
  public static final int LIST_FETCH_LIMIT = 1000;

  public static final int NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION = 80;

  public static final int NUMBER_OF_RETRIES_ON_CONFLICT = 5;

  public static final String OPTIMIZE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

  public static final String DECISION_DEFINITION_TYPE = "decision-definition";
  public static final String DECISION_INSTANCE_TYPE = "decision-instance";

  public static final String SINGLE_PROCESS_REPORT_TYPE = "single-process-report";
  public static final String SINGLE_DECISION_REPORT_TYPE = "single-decision-report";
  public static final String COMBINED_REPORT_TYPE = "combined-report";
  public static final String DASHBOARD_TYPE = "dashboard";
  public static final String COLLECTION_TYPE = "collection";
  public static final String PROC_DEF_TYPE = "process-definition";
  public static final String PROC_INSTANCE_TYPE = "process-instance";
  public static final String IMPORT_INDEX_TYPE = "import-index";
  public static final String LICENSE_TYPE = "license";
  public static final String ALERT_TYPE = "alert";
  public static final String REPORT_SHARE_TYPE = "report-share";
  public static final String DASHBOARD_SHARE_TYPE =  "dashboard-share";
  public static final String TIMESTAMP_BASED_IMPORT_INDEX_TYPE = "timestamp-based-import-index";
  public static final String METADATA_TYPE = "metadata";
  public static final String TERMINATED_USER_SESSION_TYPE = "terminated-user-session";
  public static final String TENANT_TYPE = "tenant";

  public static final String METADATA_TYPE_SCHEMA_VERSION = "schemaVersion";
}
