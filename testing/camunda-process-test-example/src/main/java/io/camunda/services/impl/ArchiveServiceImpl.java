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
