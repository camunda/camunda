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
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import org.agrona.DirectBuffer;

public class BrokerResolveIncidentRequest extends BrokerExecuteCommand<IncidentRecord> {

  private final IncidentRecord requestDto = new IncidentRecord();

  public BrokerResolveIncidentRequest(long incidentKey) {
    super(ValueType.INCIDENT, IncidentIntent.RESOLVE);
    request.setKey(incidentKey);
  }

  @Override
  public IncidentRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected IncidentRecord toResponseDto(DirectBuffer buffer) {
    final IncidentRecord responseDto = new IncidentRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
