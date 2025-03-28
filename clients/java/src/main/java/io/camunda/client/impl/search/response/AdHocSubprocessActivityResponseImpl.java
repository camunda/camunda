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

import io.camunda.client.api.search.response.AdHocSubprocessActivityResponse;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AdHocSubprocessActivityResult;
import io.camunda.client.protocol.rest.AdHocSubprocessActivitySearchQueryResult;
import java.util.List;
import java.util.stream.Collectors;

public class AdHocSubprocessActivityResponseImpl implements AdHocSubprocessActivityResponse {

  private final List<AdHocSubprocessActivity> items;

  public AdHocSubprocessActivityResponseImpl(
      final AdHocSubprocessActivitySearchQueryResult response) {
    items =
        response.getItems().stream()
            .map(AdHocSubprocessActivityImpl::new)
            .collect(Collectors.toList());
  }

  @Override
  public List<AdHocSubprocessActivity> getItems() {
    return items;
  }

  public static class AdHocSubprocessActivityImpl implements AdHocSubprocessActivity {
    private final Long processDefinitionKey;
    private final String processDefinitionId;
    private final String adHocSubprocessId;
    private final String elementId;
    private final String elementName;
    private final io.camunda.client.api.search.enums.AdHocSubprocessActivityResult.Type type;
    private final String documentation;
    private final String tenantId;

    public AdHocSubprocessActivityImpl(final AdHocSubprocessActivityResult result) {
      processDefinitionKey = Long.valueOf(result.getProcessDefinitionKey());
      processDefinitionId = result.getProcessDefinitionId();
      adHocSubprocessId = result.getAdHocSubprocessId();
      elementId = result.getElementId();
      elementName = result.getElementName();
      type =
          EnumUtil.convert(
              result.getType(),
              io.camunda.client.api.search.enums.AdHocSubprocessActivityResult.Type.class);

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
    public String getAdHocSubprocessId() {
      return adHocSubprocessId;
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
    public io.camunda.client.api.search.enums.AdHocSubprocessActivityResult.Type getType() {
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
