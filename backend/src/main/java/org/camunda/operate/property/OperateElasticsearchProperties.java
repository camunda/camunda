/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.property;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class OperateElasticsearchProperties extends ElasticsearchProperties {

  public static final String IMPORT_POSITION_INDEX_PATTERN = "operate-import-position";
  public static final String WORKFLOW_INSTANCE_INDEX_PATTERN = "operate-workflow-instance";
  public static final String ACTIVITY_INSTANCE_INDEX_PATTERN = "operate-activity-instance";
  public static final String VARIABLE_INDEX_PATTERN = "operate-variable";
  public static final String LIST_VIEW_INDEX_PATTERN = "operate-list-view";
  public static final String OPERATION_INDEX_PATTERN = "operate-operation";
  public static final String EVENT_INDEX_PATTERN = "operate-event";
  public static final String WORKFLOW_INDEX_PATTERN = "operate-workflow";

  private String importPositionIndexName = IMPORT_POSITION_INDEX_PATTERN +"_";
  private String eventIndexName = EVENT_INDEX_PATTERN +"_";
  private String workflowInstanceIndexName = WORKFLOW_INSTANCE_INDEX_PATTERN +"_";
  private String activityInstanceIndexName = ACTIVITY_INSTANCE_INDEX_PATTERN +"_";
  private String variableIndexName = VARIABLE_INDEX_PATTERN +"_";
  private String listViewIndexName = LIST_VIEW_INDEX_PATTERN +"_";
  private String operationIndexName = OPERATION_INDEX_PATTERN +"_";
  private String workflowIndexName = WORKFLOW_INDEX_PATTERN +"_";
  private String importPositionAlias = IMPORT_POSITION_INDEX_PATTERN + "_alias";
  private String workflowAlias = WORKFLOW_INDEX_PATTERN + "_alias";

  private int templateOrder = 30;

  private boolean rolloverEnabled = true;

  /**
   * This format will be used to create timed indices. It must correspond to rolloverInterval parameter.
   */
  private String rolloverDateFormat = "yyyyMMdd";
  /**
   * Interval description for "date histogram" aggregation, which is used to group finished instances.
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-datehistogram-aggregation.html">Elasticsearch docs</a>
   */
  private String rolloverInterval = "1d";
  private int rolloverBatchSize = 100;

  @NestedConfigurationProperty
  private TermsQueryProperties terms = new TermsQueryProperties();

  public String getWorkflowInstanceIndexName() {
    return workflowInstanceIndexName;
  }

  public void setWorkflowInstanceIndexName(String workflowInstanceIndexName) {
    this.workflowInstanceIndexName = workflowInstanceIndexName;
  }

  public String getActivityInstanceIndexName() {
    return activityInstanceIndexName;
  }

  public void setActivityInstanceIndexName(String activityInstanceIndexName) {
    this.activityInstanceIndexName = activityInstanceIndexName;
  }

  public String getVariableIndexName() {
    return variableIndexName;
  }

  public void setVariableIndexName(String variableIndexName) {
    this.variableIndexName = variableIndexName;
  }

  public String getListViewIndexName() {
    return listViewIndexName;
  }

  public void setListViewIndexName(String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
  }

  public String getOperationIndexName() {
    return operationIndexName;
  }

  public void setOperationIndexName(String operationIndexName) {
    this.operationIndexName = operationIndexName;
  }

  public String getWorkflowIndexName() {
    return workflowIndexName;
  }

  public void setWorkflowIndexName(String workflowIndexName) {
    this.workflowIndexName = workflowIndexName;
  }

  public String getEventIndexName() {
    return eventIndexName;
  }

  public void setEventIndexName(String eventIndexName) {
    this.eventIndexName = eventIndexName;
  }

  public String getImportPositionIndexName() {
    return importPositionIndexName;
  }

  public void setImportPositionIndexName(String importPositionIndexName) {
    this.importPositionIndexName = importPositionIndexName;
  }

  public String getImportPositionAlias() {
    return importPositionAlias;
  }

  public void setImportPositionAlias(String importPositionAlias) {
    this.importPositionAlias = importPositionAlias;
  }

  public String getWorkflowAlias() {
    return workflowAlias;
  }

  public void setWorkflowAlias(String workflowAlias) {
    this.workflowAlias = workflowAlias;
  }

  public int getTemplateOrder() {
    return templateOrder;
  }

  public void setTemplateOrder(int templateOrder) {
    this.templateOrder = templateOrder;
  }

  public boolean isRolloverEnabled() {
    return rolloverEnabled;
  }

  public void setRolloverEnabled(boolean rolloverEnabled) {
    this.rolloverEnabled = rolloverEnabled;
  }

  public String getRolloverDateFormat() {
    return rolloverDateFormat;
  }

  public void setRolloverDateFormat(String rolloverDateFormat) {
    this.rolloverDateFormat = rolloverDateFormat;
  }

  public String getRolloverInterval() {
    return rolloverInterval;
  }

  public void setRolloverInterval(String rolloverInterval) {
    this.rolloverInterval = rolloverInterval;
  }

  public int getRolloverBatchSize() {
    return rolloverBatchSize;
  }

  public void setRolloverBatchSize(int rolloverBatchSize) {
    this.rolloverBatchSize = rolloverBatchSize;
  }

  public TermsQueryProperties getTerms() {
    return terms;
  }

  public void setTerms(TermsQueryProperties terms) {
    this.terms = terms;
  }
}
