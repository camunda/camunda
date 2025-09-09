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

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.sdk.annotation.JobWorker;
import io.camunda.sdk.annotation.Variable;
import io.camunda.sdk.exception.BpmnError;
import io.camunda.services.ArchiveService;
import io.camunda.services.WiredLegacyException;
import org.springframework.stereotype.Component;

@Component
public class ArchiveInvoiceWorker {

  private final ArchiveService service;

  public ArchiveInvoiceWorker(final ArchiveService service) {
    this.service = service;
  }

  @JobWorker(type = "archive-invoice")
  public void handleJob(
      @Variable("invoiceId") final String invoiceId,
      @Variable("invoice") final JsonNode invoiceJson) {
    try {
      service.archiveInvoice(invoiceId, invoiceJson);
    } catch (final WiredLegacyException e) {
      throw new BpmnError(
          "LEGACY_ERROR_ARCHIVE", "The archive system had a problem: " + e.getMessage());
    }
  }
}
