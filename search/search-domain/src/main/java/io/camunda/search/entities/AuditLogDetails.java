/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = AuditLogDetails.AuditLogFailDetails.class,
      name = AuditLogDetails.TYPE_FAIL),
  @JsonSubTypes.Type(
      value = AuditLogDetails.AuditLogChangeSetDetails.class,
      name = AuditLogDetails.TYPE_CHANGE_SET),
  @JsonSubTypes.Type(
      value = AuditLogDetails.AuditLogInstructionDetails.class,
      name = AuditLogDetails.TYPE_INSTRUCTION)
})
public abstract class AuditLogDetails {

  public static final ObjectMapper MAPPER = new ObjectMapper();
  public static final String TYPE_FAIL = "FAIL";
  public static final String TYPE_CHANGE_SET = "CHANGE_SET";
  public static final String TYPE_INSTRUCTION = "INSTRUCTION";

  protected AuditLogDetailsType type;

  public AuditLogDetails(final AuditLogDetailsType type) {
    this.type = type;
  }

  public AuditLogDetailsType getType() {
    return type;
  }

  public void setType(final AuditLogDetailsType type) {
    this.type = type;
  }

  public String writeValueAsString() {
    try {
      return MAPPER.writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize AuditLogDetails to JSON", e);
    }
  }

  public static AuditLogDetails readValue(final String json) {
    try {
      return MAPPER.readValue(json, AuditLogDetails.class);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to deserialize AuditLogDetails from JSON", e);
    }
  }

  public static class AuditLogFailDetails extends AuditLogDetails {
    private String code;
    private String message;

    // Jackson needs a default constructor
    public AuditLogFailDetails() {
      super(AuditLogDetailsType.FAIL);
    }

    public AuditLogFailDetails(final String code, final String message) {
      super(AuditLogDetailsType.FAIL);
      this.code = code;
      this.message = message;
    }

    public String getCode() {
      return code;
    }

    public String getMessage() {
      return message;
    }
  }

  public static final class AuditLogChangeSetDetails extends AuditLogDetails {
    private List<ChangeSet> changes;

    // Jackson needs a default constructor
    public AuditLogChangeSetDetails() {
      super(AuditLogDetailsType.CHANGE_SET);
    }

    public AuditLogChangeSetDetails(final List<ChangeSet> changes) {
      super(AuditLogDetailsType.CHANGE_SET);
      this.changes = changes;
    }

    public List<ChangeSet> getChanges() {
      return changes;
    }
  }

  public static final class AuditLogInstructionDetails extends AuditLogDetails {
    private List<Instruction> startInstructions;
    private List<Instruction> runtimeInstructions;

    // Jackson needs a default constructor
    public AuditLogInstructionDetails() {
      super(AuditLogDetailsType.INSTRUCTION);
    }

    public AuditLogInstructionDetails(
        final List<Instruction> startInstructions, final List<Instruction> runtimeInstructions) {
      super(AuditLogDetailsType.INSTRUCTION);
      this.startInstructions = startInstructions;
      this.runtimeInstructions = runtimeInstructions;
    }

    public List<Instruction> getStartInstructions() {
      return startInstructions;
    }

    public List<Instruction> getRuntimeInstructions() {
      return runtimeInstructions;
    }
  }

  public record ChangeSet(String name, String oldValue, String newValue) {}

  public record Instruction(String elementId) {}

  public enum AuditLogDetailsType {
    FAIL,
    CHANGE_SET,
    INSTRUCTION
  }
}
