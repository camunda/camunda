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
package io.camunda.zeebe.client.impl.response;

import io.camunda.zeebe.client.api.response.Form;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FormMetadata;
import java.util.Objects;

public class FormImpl implements Form {

  private final String formId;
  private final int version;
  private final long formKey;
  private final String resourceName;
  private final String tenantId;

  public FormImpl(final FormMetadata formMetadata) {
    this(
        formMetadata.getFormId(),
        formMetadata.getVersion(),
        formMetadata.getFormKey(),
        formMetadata.getResourceName(),
        formMetadata.getTenantId());
  }

  public FormImpl(
      final String formId,
      final int version,
      final long formKey,
      final String resourceName,
      final String tenantId) {
    this.formId = formId;
    this.version = version;
    this.formKey = formKey;
    this.resourceName = resourceName;
    this.tenantId = tenantId;
  }

  @Override
  public String getFormId() {
    return formId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getFormKey() {
    return formKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(formId, version, formKey, resourceName, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FormImpl form = (FormImpl) o;
    return version == form.version
        && formKey == form.formKey
        && Objects.equals(formId, form.getFormId())
        && Objects.equals(resourceName, form.resourceName)
        && Objects.equals(tenantId, form.tenantId);
  }

  @Override
  public String toString() {
    return "FormImpl{"
        + "formId='"
        + formId
        + '\''
        + ", version="
        + version
        + '\''
        + ", formKey="
        + formKey
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
