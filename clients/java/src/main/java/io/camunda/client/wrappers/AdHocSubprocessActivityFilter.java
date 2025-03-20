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
package io.camunda.client.wrappers;

public class AdHocSubprocessActivityFilter {

  private String processDefinitionKey;
  private String adHocSubprocessId;

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getAdHocSubprocessId() {
    return adHocSubprocessId;
  }

  public void setAdHocSubprocessId(String adHocSubprocessId) {
    this.adHocSubprocessId = adHocSubprocessId;
  }

  public static io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter toProtocolObject(
      AdHocSubprocessActivityFilter object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter protocolObject =
        new io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter();
    protocolObject.setProcessDefinitionKey(object.processDefinitionKey);
    protocolObject.setAdHocSubprocessId(object.adHocSubprocessId);
    return protocolObject;
  }

  public static AdHocSubprocessActivityFilter fromProtocolObject(
      io.camunda.client.protocol.rest.AdHocSubprocessActivityFilter protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final AdHocSubprocessActivityFilter object = new AdHocSubprocessActivityFilter();
    object.processDefinitionKey = protocolObject.getProcessDefinitionKey();
    object.adHocSubprocessId = protocolObject.getAdHocSubprocessId();
    return object;
  }
}
