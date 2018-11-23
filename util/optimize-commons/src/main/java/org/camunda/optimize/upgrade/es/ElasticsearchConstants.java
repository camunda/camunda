package org.camunda.optimize.upgrade.es;

public class ElasticsearchConstants {

  // Note: we cap listings to 1000 as a generous practical limit, no paging
  public static final int LIST_FETCH_LIMIT = 1000;

  public static final String DECISION_DEFINITION_TYPE = "decision-definition";
  public static final String DECISION_INSTANCE_TYPE = "decision-instance";

  public static final String SINGLE_REPORT_TYPE = "single-report";
  public static final String COMBINED_REPORT_TYPE = "combined-report";
  public static final String DASHBOARD_TYPE = "dashboard";

  public static final String COLLECTION_TYPE = "collection";

  public static final String TIMESTAMP_BASED_IMPORT_INDEX_TYPE = "timestamp-based-import-index";

  public static final String METADATA_TYPE_SCHEMA_VERSION = "schemaVersion";

  public static final String DELETE_SUCCESSFUL_RESPONSE_RESULT = "deleted";
  public static final String CREATE_SUCCESSFUL_RESPONSE_RESULT = "created";
  public static final String UPDATE_SUCCESSFUL_RESPONSE_RESULT = "updated";
}
