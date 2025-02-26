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
package io.camunda.process.test.dto;

public class CamundaClientConnection {

  private String restAddress;
  private String grpcAddress;

  public CamundaClientConnection() {}

  public CamundaClientConnection(final String restAddress, final String grpcAddress) {
    this.restAddress = restAddress;
    this.grpcAddress = grpcAddress;
  }

  public String getRestAddress() {
    return restAddress;
  }

  public void setRestAddress(final String restAddress) {
    this.restAddress = restAddress;
  }

  public String getGrpcAddress() {
    return grpcAddress;
  }

  public void setGrpcAddress(final String grpcAddress) {
    this.grpcAddress = grpcAddress;
  }
}
