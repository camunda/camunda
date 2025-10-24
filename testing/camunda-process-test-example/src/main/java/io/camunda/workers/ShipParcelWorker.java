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

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.services.ShippingService;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ShipParcelWorker {

  private final ShippingService service;

  public ShipParcelWorker(final ShippingService service) {
    this.service = service;
  }

  @JobWorker(type = "ship-parcel")
  public Map<String, Object> handleJob(
      final ActivatedJob job, @Variable("order_id") final String orderId) {
    final String shippingId = service.shipOrder(orderId);
    return Map.of("shipping_id", shippingId);
  }
}
