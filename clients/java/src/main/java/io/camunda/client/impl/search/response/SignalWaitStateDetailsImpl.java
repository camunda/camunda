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

import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.SignalWaitStateDetails;

public class SignalWaitStateDetailsImpl implements SignalWaitStateDetails {

  private final String signalName;

  public SignalWaitStateDetailsImpl(
      final io.camunda.client.protocol.rest.SignalWaitStateDetails details) {
    signalName = details.getSignalName();
  }

  @Override
  public WaitStateType getWaitStateType() {
    return WaitStateType.SIGNAL;
  }

  @Override
  public String getSignalName() {
    return signalName;
  }
}
