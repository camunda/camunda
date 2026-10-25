/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;

/**
 * Gates a job worker task on the consent of a person. The task names the connection and the
 * operation it acts on, as fixed values. Without a subject, the subject is whoever started the
 * process instance.
 */
public interface ZeebeConsent extends BpmnModelElementInstance {

  String getConnection();

  void setConnection(String connection);

  String getOperation();

  void setOperation(String operation);

  /** An expression that gives the Camunda username of the person whose record changes. */
  String getSubject();

  void setSubject(String subject);

  /** An expression whose value the approval shows and binds. */
  String getChange();

  void setChange(String change);
}
