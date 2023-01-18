/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.es;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ElasticsearchConstants {

  // Note: we cap listings to 1000 as a generous practical limit, no paging
  public static final int LIST_FETCH_LIMIT = 1000;

  public static final int MAX_RESPONSE_SIZE_LIMIT = 10_000;

  public static final int NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION = 80;

  public static final int NUMBER_OF_RETRIES_ON_CONFLICT = 5;

  public static final int IGNORE_ABOVE_CHAR_LIMIT = 7000;

  public static final String OPTIMIZE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

  public static final String NUMBER_OF_REPLICAS_SETTING = "number_of_replicas";
  public static final String NUMBER_OF_SHARDS_SETTING = "number_of_shards";
  public static final String REFRESH_INTERVAL_SETTING = "refresh_interval";
  public static final String MAPPING_NESTED_OBJECTS_LIMIT = "mapping.nested_objects.limit";
  public static final String IGNORE_ABOVE_SETTING = "ignore_above";
  public static final String IGNORE_MALFORMED = "ignore_malformed";
  public static final String ANALYSIS_SETTING = "analysis";
  public static final String SORT_SETTING = "sort";
  public static final String SORT_FIELD_SETTING = "field";
  public static final String SORT_ORDER_SETTING = "order";
  public static final String SORT_NULLS_FIRST = "_first";
  public static final String SORT_NULLS_LAST = "_last";
  public static final String FIELDS = "fields";
  public static final String LOWERCASE_NGRAM = "lowercase_ngram";
  public static final String LOWERCASE_NORMALIZER = "lowercase_normalizer";
  public static final String MAPPING_ENABLED_SETTING = "enabled";

  public static final String MAPPING_PROPERTY_TYPE = "type";
  public static final String PROPERTIES_PROPERTY_TYPE = "properties";
  public static final String FORMAT_PROPERTY_TYPE = "format";
  public static final String DYNAMIC_PROPERTY_TYPE = "dynamic";

  public static final String TYPE_KEYWORD = "keyword";
  public static final String TYPE_INTEGER = "integer";
  public static final String TYPE_LONG = "long";
  public static final String TYPE_DOUBLE = "double";
  public static final String TYPE_DATE = "date";
  public static final String TYPE_BOOLEAN = "boolean";
  public static final String TYPE_OBJECT = "object";
  public static final String TYPE_TEXT = "text";
  public static final String TYPE_NESTED = "nested";

  public static final String DECISION_DEFINITION_INDEX_NAME = "decision-definition";
  public static final String DECISION_INSTANCE_MULTI_ALIAS = "decision-instance";
  public static final String DECISION_INSTANCE_INDEX_PREFIX = "decision-instance-";

  public static final String PROCESS_DEFINITION_INDEX_NAME = "process-definition";
  public static final String PROCESS_INSTANCE_MULTI_ALIAS = "process-instance";
  public static final String PROCESS_INSTANCE_INDEX_PREFIX = "process-instance-";
  public static final String PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX = "process-instance-archive-";

  public static final String SINGLE_PROCESS_REPORT_INDEX_NAME = "single-process-report";
  public static final String SINGLE_DECISION_REPORT_INDEX_NAME = "single-decision-report";
  public static final String COMBINED_REPORT_INDEX_NAME = "combined-report";
  public static final String DASHBOARD_INDEX_NAME = "dashboard";
  public static final String COLLECTION_INDEX_NAME = "collection";
  public static final String IMPORT_INDEX_INDEX_NAME = "import-index";
  public static final String LICENSE_INDEX_NAME = "license";
  public static final String ALERT_INDEX_NAME = "alert";
  public static final String REPORT_SHARE_INDEX_NAME = "report-share";
  public static final String DASHBOARD_SHARE_INDEX_NAME = "dashboard-share";
  public static final String TIMESTAMP_BASED_IMPORT_INDEX_NAME = "timestamp-based-import-index";
  public static final String POSITION_BASED_IMPORT_INDEX_NAME = "position-based-import-index";
  public static final String METADATA_INDEX_NAME = "metadata";
  public static final String TERMINATED_USER_SESSION_INDEX_NAME = "terminated-user-session";
  public static final String TENANT_INDEX_NAME = "tenant";
  public static final String EXTERNAL_EVENTS_INDEX_NAME = "event";
  public static final String EVENT_PROCESS_MAPPING_INDEX_NAME = "event-process-mapping";
  public static final String VARIABLE_LABEL_INDEX_NAME = "variable-label";
  public static final String PROCESS_OVERVIEW_INDEX_NAME = "process-overview";
  public static final String INSTANT_DASHBOARD_INDEX_NAME = "instant-dashboard";

  public static final String ZEEBE_PROCESS_DEFINITION_INDEX_NAME = "process";
  public static final String ZEEBE_PROCESS_INSTANCE_INDEX_NAME = "process-instance";
  public static final String ZEEBE_VARIABLE_INDEX_NAME = "variable";
  public static final String ZEEBE_INCIDENT_INDEX_NAME = "incident";

  public static final String EVENT_PROCESS_DEFINITION_INDEX_NAME = "event-process-definition";
  public static final String EVENT_PROCESS_PUBLISH_STATE_INDEX_NAME = "event-process-publish-state";
  public static final String ONBOARDING_INDEX_NAME = "onboarding-state";
  public static final String VARIABLE_UPDATE_INSTANCE_INDEX_NAME = "variable-update-instance";
  public static final String BUSINESS_KEY_INDEX_NAME = "business-key";
  public static final String SETTINGS_INDEX_NAME = "settings";
  public static final String EXTERNAL_PROCESS_VARIABLE_INDEX_NAME = "external-process-variable";

  public static final String EVENT_PROCESS_INSTANCE_INDEX_PREFIX = "event-process-instance-";
  public static final String CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX = "camunda-activity-event-";

  public static final String INDEX_SUFFIX_PRE_ROLLOVER = "-000001";

  public static final String EVENT_SEQUENCE_COUNT_INDEX_PREFIX = "event-sequence-count-";
  public static final String EVENT_TRACE_STATE_INDEX_PREFIX = "event-trace-state-";
  public static final String EXTERNAL_EVENTS_INDEX_SUFFIX = "external";

  public static final String EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX = "eventStateProcessing-";

  public static final String INDEX = "_index";

  public static final String TOO_MANY_BUCKETS_EXCEPTION_TYPE = "too_many_buckets_exception";
  public static final String INDEX_NOT_FOUND_EXCEPTION_TYPE = "index_not_found_exception";
  public static final String INDEX_ALREADY_EXISTS_EXCEPTION_TYPE = "resource_already_exists_exception";

  // used to reference Optimize as the source "engine" of imported data
  public static final String ENGINE_ALIAS_OPTIMIZE = "optimize";

  public static final String ENGINE_DATA_SOURCE = "engine";
  public static final String ZEEBE_DATA_SOURCE = "zeebe";
  public static final String EVENTS_DATA_SOURCE = "events";
  public static final String INGESTED_DATA_SOURCE = "ingested";

  // Aggregation constants
  public static final String FREQUENCY_AGGREGATION = "_frequency";

}
