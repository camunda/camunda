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

public class JobChangeset {

  private Integer retries;
  private Long timeout;

  public Integer getRetries() {
    return retries;
  }

  public JobChangeset setRetries(Integer retries) {
    this.retries = retries;
    return this;
  }

  public Long getTimeout() {
    return timeout;
  }

  public JobChangeset setTimeout(Long timeout) {
    this.timeout = timeout;
    return this;
  }

  public static io.camunda.client.protocol.rest.JobChangeset toProtocolObject(JobChangeset object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.JobChangeset protocolObject =
        new io.camunda.client.protocol.rest.JobChangeset();
    protocolObject.setRetries(object.retries);
    protocolObject.setTimeout(object.timeout);
    return protocolObject;
  }

  public static JobChangeset fromProtocolObject(
      io.camunda.client.protocol.rest.JobChangeset protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final JobChangeset object = new JobChangeset();
    object.retries = protocolObject.getRetries();
    object.timeout = protocolObject.getTimeout();
    return object;
  }
}
