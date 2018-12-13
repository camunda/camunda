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

public class OperateElasticsearchProperties extends ElasticsearchProperties {

  public static final String IMPORT_POSITION_INDEX_PATTERN = "operate-import-position";
  public static final String WORKFLOW_INSTANCE_INDEX_PATTERN = "operate-workflow-instance";
  public static final String EVENT_INDEX_PATTERN = "operate-event";
  public static final String WORKFLOW_INDEX_PATTERN = "operate-workflow";

  private String importPositionIndexName = IMPORT_POSITION_INDEX_PATTERN + "_";
  private String eventIndexName = EVENT_INDEX_PATTERN + "_";
  private String workflowInstanceIndexName = WORKFLOW_INSTANCE_INDEX_PATTERN + "_";
  private String workflowIndexName = WORKFLOW_INDEX_PATTERN + "_";
  private String importPositionAlias = IMPORT_POSITION_INDEX_PATTERN;
  private String workflowAlias = WORKFLOW_INDEX_PATTERN;

  private int templateOrder = 30;

  public String getWorkflowInstanceIndexName() {
    return workflowInstanceIndexName;
  }

  public void setWorkflowInstanceIndexName(String workflowInstanceIndexName) {
    this.workflowInstanceIndexName = workflowInstanceIndexName;
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
}
