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
package io.camunda.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.services.ArchiveService;
import org.springframework.stereotype.Component;

@Component
public class ArchiveServiceImpl implements ArchiveService {

  @Override
  public void archiveInvoice(String invoiceId, JsonNode invoiceJson) {
    // This would now call the real Archive API - probably injected via Spring
  }
}
