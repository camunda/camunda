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
package io.camunda.client.impl.command;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.AssignMappingRuleToGroupStep1;
import io.camunda.client.api.command.AssignMappingRuleToGroupStep1.AssignMappingRuleToGroupStep2;
import io.camunda.client.api.command.AssignMappingRuleToGroupStep1.AssignMappingRuleToGroupStep3;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignMappingRuleToGroupResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.AssignMappingRuleToGroupResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class AssignMappingRuleToGroupCommandImpl
    implements AssignMappingRuleToGroupStep1,
        AssignMappingRuleToGroupStep2,
        AssignMappingRuleToGroupStep3 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String mappingRuleId;
  private String groupId;

  public AssignMappingRuleToGroupCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignMappingRuleToGroupStep2 mappingRuleId(final String mappingRuleId) {
    this.mappingRuleId = mappingRuleId;
    return this;
  }

  @Override
  public AssignMappingRuleToGroupStep3 groupId(final String groupId) {
    this.groupId = groupId;
    return this;
  }

  @Override
  public FinalCommandStep<AssignMappingRuleToGroupResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignMappingRuleToGroupResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);
    ArgumentUtil.ensureNotNullNorEmpty("mappingRuleId", mappingRuleId);
    final HttpCamundaFuture<AssignMappingRuleToGroupResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/groups/" + groupId + "/mapping-rules/" + mappingRuleId,
        null,
        httpRequestConfig.build(),
        AssignMappingRuleToGroupResponseImpl::new,
        result);
    return result;
  }
}
