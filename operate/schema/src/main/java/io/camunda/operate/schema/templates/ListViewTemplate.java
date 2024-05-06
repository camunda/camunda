/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.schema.templates;

import io.camunda.operate.schema.backup.Prio2Backup;
import org.springframework.stereotype.Component;

@Component
public class ListViewTemplate extends AbstractTemplateDescriptor implements Prio2Backup {

  public static final String INDEX_NAME = "list-view";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROCESS_VERSION = "processVersion";
  public static final String PROCESS_KEY = "processDefinitionKey";
  public static final String PROCESS_NAME = "processName";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String STATE = "state";
  public static final String PARENT_PROCESS_INSTANCE_KEY = "parentProcessInstanceKey";
  public static final String PARENT_FLOW_NODE_INSTANCE_KEY = "parentFlowNodeInstanceKey";
  public static final String TREE_PATH = "treePath";

  public static final String ACTIVITY_ID = "activityId";
  public static final String ACTIVITY_STATE = "activityState";
  public static final String ACTIVITY_TYPE = "activityType";
  public static final String ERROR_MSG = "errorMessage";
  public static final String JOB_FAILED_WITH_RETRIES_LEFT = "jobFailedWithRetriesLeft";

  // used both for process instance and flow node instance
  public static final String INCIDENT = "incident"; // true/false

  public static final String INCIDENT_POSITION = "positionIncident";
  public static final String JOB_POSITION = "positionJob";

  public static final String VAR_NAME = "varName";
  public static final String VAR_VALUE = "varValue";
  public static final String SCOPE_KEY = "scopeKey";

  public static final String BATCH_OPERATION_IDS = "batchOperationIds";

  public static final String JOIN_RELATION = "joinRelation";
  public static final String PROCESS_INSTANCE_JOIN_RELATION = "processInstance";
  public static final String ACTIVITIES_JOIN_RELATION =
      "activity"; // now we call it flow node instance
  public static final String VARIABLES_JOIN_RELATION = "variable";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.3.0";
  }
}
