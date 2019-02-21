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

public class TermsQueryProperties {

  private int maxWorkflowCount = 100;
  private int maxUniqueBpmnProcessIdCount = 50;
  private int maxVersionOfOneWorkflowCount = 50;
  private int maxIncidentErrorMessageCount = 100;
  private int maxFlowNodesInOneWorkflow = 200;

  public int getMaxWorkflowCount() {
    return maxWorkflowCount;
  }

  public void setMaxWorkflowCount(int maxWorkflowCount) {
    this.maxWorkflowCount = maxWorkflowCount;
  }

  public int getMaxUniqueBpmnProcessIdCount() {
    return maxUniqueBpmnProcessIdCount;
  }

  public void setMaxUniqueBpmnProcessIdCount(int maxUniqueBpmnProcessIdCount) {
    this.maxUniqueBpmnProcessIdCount = maxUniqueBpmnProcessIdCount;
  }

  public int getMaxVersionOfOneWorkflowCount() {
    return maxVersionOfOneWorkflowCount;
  }

  public void setMaxVersionOfOneWorkflowCount(int maxVersionOfOneWorkflowCount) {
    this.maxVersionOfOneWorkflowCount = maxVersionOfOneWorkflowCount;
  }

  public int getMaxIncidentErrorMessageCount() {
    return maxIncidentErrorMessageCount;
  }

  public void setMaxIncidentErrorMessageCount(int maxIncidentErrorMessageCount) {
    this.maxIncidentErrorMessageCount = maxIncidentErrorMessageCount;
  }

  public int getMaxFlowNodesInOneWorkflow() {
    return maxFlowNodesInOneWorkflow;
  }

  public void setMaxFlowNodesInOneWorkflow(int maxFlowNodesInOneWorkflow) {
    this.maxFlowNodesInOneWorkflow = maxFlowNodesInOneWorkflow;
  }
}
