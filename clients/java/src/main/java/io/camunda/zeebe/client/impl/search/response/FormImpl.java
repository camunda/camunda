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
package io.camunda.zeebe.client.impl.search.response;

import io.camunda.zeebe.client.api.search.response.Form;
import io.camunda.zeebe.client.protocol.rest.FormItem;

public class FormImpl implements Form {

  private final String bpmnId;
  private final long version;
  private final long key;
  private final Object schema;
  private final String tenantId;

  public FormImpl(final FormItem item) {
    this.bpmnId = item.getBpmnId();
    this.version = item.getVersion();
    this.key = item.getKey();
    this.schema = item.getSchema();
    this.tenantId = item.getTenantId();
  }

  @Override
  public String getBpmnId() {
    return bpmnId;
  }

  @Override
  public long getVersion() {
    return version;
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public Object getSchema() {
    return schema;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }
}
