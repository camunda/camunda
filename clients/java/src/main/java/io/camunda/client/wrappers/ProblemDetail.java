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

import java.net.URI;

public class ProblemDetail {

  private URI type = URI.create("about:blank");
  private String title;
  private Integer status;
  private String detail;
  private URI instance;

  public URI getType() {
    return type;
  }

  public ProblemDetail setType(URI type) {
    this.type = type;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public ProblemDetail setTitle(String title) {
    this.title = title;
    return this;
  }

  public Integer getStatus() {
    return status;
  }

  public ProblemDetail setStatus(Integer status) {
    this.status = status;
    return this;
  }

  public String getDetail() {
    return detail;
  }

  public ProblemDetail setDetail(String detail) {
    this.detail = detail;
    return this;
  }

  public URI getInstance() {
    return instance;
  }

  public ProblemDetail setInstance(URI instance) {
    this.instance = instance;
    return this;
  }

  public static io.camunda.client.protocol.rest.ProblemDetail toProtocolObject(
      ProblemDetail object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.ProblemDetail protocolObject =
        new io.camunda.client.protocol.rest.ProblemDetail();
    protocolObject.setType(object.type);
    protocolObject.setTitle(object.title);
    protocolObject.setStatus(object.status);
    protocolObject.setDetail(object.detail);
    protocolObject.setInstance(object.instance);
    return protocolObject;
  }

  public static ProblemDetail fromProtocolObject(
      io.camunda.client.protocol.rest.ProblemDetail protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final ProblemDetail object = new ProblemDetail();
    object.type = protocolObject.getType();
    object.title = protocolObject.getTitle();
    object.status = protocolObject.getStatus();
    object.detail = protocolObject.getDetail();
    object.instance = protocolObject.getInstance();
    return object;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("class ProblemDetail {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    detail: ").append(toIndentedString(detail)).append("\n");
    sb.append("    instance: ").append(toIndentedString(instance)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}
