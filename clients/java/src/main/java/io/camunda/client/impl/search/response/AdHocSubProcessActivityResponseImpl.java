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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.enums.AdHocSubProcessActivityResultType;
import io.camunda.client.api.search.response.AdHocSubProcessActivityResponse;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AdHocSubProcessActivityResult;
import io.camunda.client.protocol.rest.AdHocSubProcessActivitySearchQueryResult;
import java.util.List;
import java.util.stream.Collectors;

public class AdHocSubProcessActivityResponseImpl implements AdHocSubProcessActivityResponse {

  private final List<AdHocSubProcessActivity> items;

  public AdHocSubProcessActivityResponseImpl(
      final AdHocSubProcessActivitySearchQueryResult response) {
    items =
        response.getItems().stream()
            .map(AdHocSubProcessActivityImpl::new)
            .collect(Collectors.toList());
  }

  @Override
  public List<AdHocSubProcessActivity> getItems() {
    return items;
  }

  public static class AdHocSubProcessActivityImpl implements AdHocSubProcessActivity {
    private final Long processDefinitionKey;
    private final String processDefinitionId;
    private final String adHocSubProcessId;
    private final String elementId;
    private final String elementName;
    private final AdHocSubProcessActivityResultType type;
    private final String documentation;
    private final String tenantId;

    public AdHocSubProcessActivityImpl(final AdHocSubProcessActivityResult result) {
      processDefinitionKey = Long.valueOf(result.getProcessDefinitionKey());
      processDefinitionId = result.getProcessDefinitionId();
      adHocSubProcessId = result.getAdHocSubProcessId();
      elementId = result.getElementId();
      elementName = result.getElementName();
      type = EnumUtil.convert(result.getType(), AdHocSubProcessActivityResultType.class);

      documentation = result.getDocumentation();
      tenantId = result.getTenantId();
    }

    @Override
    public Long getProcessDefinitionKey() {
      return processDefinitionKey;
    }

    @Override
    public String getProcessDefinitionId() {
      return processDefinitionId;
    }

    @Override
    public String getAdHocSubProcessId() {
      return adHocSubProcessId;
    }

    @Override
    public String getElementId() {
      return elementId;
    }

    @Override
    public String getElementName() {
      return elementName;
    }

    @Override
    public AdHocSubProcessActivityResultType getType() {
      return type;
    }

    @Override
    public String getDocumentation() {
      return documentation;
    }

    @Override
    public String getTenantId() {
      return tenantId;
    }
  }
}
