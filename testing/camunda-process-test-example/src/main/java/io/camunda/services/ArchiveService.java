package io.camunda.services;

import com.fasterxml.jackson.databind.JsonNode;

public interface ArchiveService {

  public void archiveInvoice(String invoiceId, JsonNode invoiceJson) throws WiredLegacyException;
}
