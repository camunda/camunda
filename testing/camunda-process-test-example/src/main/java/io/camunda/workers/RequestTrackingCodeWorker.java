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
package io.camunda.workers;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.services.ShippingService;
import io.camunda.spring.client.annotation.JobWorker;
import io.camunda.spring.client.annotation.Variable;
import org.springframework.stereotype.Component;

@Component
public class RequestTrackingCodeWorker {

  private final ShippingService service;

  public RequestTrackingCodeWorker(final ShippingService service) {
    this.service = service;
  }

  @JobWorker(type = "request-tracking-code")
  public void handleJob(final ActivatedJob job, @Variable("shipping_id") final String shippingId) {
    service.requestTrackingCode(shippingId);
  }
}
