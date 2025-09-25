package io.camunda.debug.cli.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.debug.cli.ProcessInstanceRelatedValue;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;

public record Record(
    long position,
    long sourceRecordPosition,
    long timestamp,
    long key,
    RecordType recordType,
    ValueType valueType,
    Intent intent,
    RejectionType rejectionType,
    String rejectionReason,
    Long requestId,
    int requestStreamId,
    int protocolVersion,
    String brokerVersion,
    Integer recordVersion,
    String authData,
    Object recordValue,
    ProcessInstanceRelatedValue piRelatedValue) {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public String toString() {
    try {
      return OBJECT_MAPPER.writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      return "{\"error\":\"Failed to serialize Record to JSON\"}";
    }
  }
}
