/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_BPMN_PROCESS_ID;
import static io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_INSTANCE_KEY;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.PackedProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class UserTaskRecord extends UnifiedRecordValue implements UserTaskRecordValue {

  public static final DirectBuffer NO_HEADERS = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

  public static final String ASSIGNEE = "assignee";
  public static final String CANDIDATE_GROUPS = "candidateGroupsList";
  public static final String CANDIDATE_USERS = "candidateUsersList";
  public static final String DUE_DATE = "dueDate";
  public static final String FOLLOW_UP_DATE = "followUpDate";
  public static final String PRIORITY = "priority";
  public static final String VARIABLES = "variables";

  private static final String EMPTY_STRING = "";
  private static final StringValue ASSIGNEE_VALUE = new StringValue(ASSIGNEE);
  private static final StringValue CANDIDATE_GROUPS_VALUE = new StringValue(CANDIDATE_GROUPS);
  private static final StringValue CANDIDATE_USERS_VALUE = new StringValue(CANDIDATE_USERS);
  private static final StringValue DUE_DATE_VALUE = new StringValue(DUE_DATE);
  private static final StringValue FOLLOW_UP_DATE_VALUE = new StringValue(FOLLOW_UP_DATE);
  private static final StringValue PRIORITY_VALUE = new StringValue(PRIORITY);
  private static final StringValue VARIABLES_VALUE = new StringValue(VARIABLES);

  /**
   * Defines the mapping between names of attributes that may be modified (updated or corrected) and
   * their corresponding getter methods. This map enables efficient comparison and updates of
   * attribute values dynamically based on their names.
   *
   * @implNote If a new updatable attribute is introduced in the {@link UserTaskRecord} class:
   *     <ul>
   *       <li>The corresponding getter method must also be added to this map.
   *       <li>To ensure efficiency, prefer getters that return a {@code DirectBuffer}
   *           representation of the attribute (where applicable) to avoid unnecessary conversions
   *           during comparisons.
   *     </ul>
   */
  private static final Map<String, Function<UserTaskRecord, ?>> ATTRIBUTE_GETTER_MAP =
      Map.of(
          ASSIGNEE, UserTaskRecord::getAssigneeBuffer,
          CANDIDATE_GROUPS, UserTaskRecord::getCandidateGroupsList,
          CANDIDATE_USERS, UserTaskRecord::getCandidateUsersList,
          DUE_DATE, UserTaskRecord::getDueDateBuffer,
          FOLLOW_UP_DATE, UserTaskRecord::getFollowUpDateBuffer,
          PRIORITY, UserTaskRecord::getPriority,
          VARIABLES, UserTaskRecord::getVariablesBuffer);

  private final LongProperty userTaskKeyProp = new LongProperty("userTaskKey", -1);
  private final StringProperty assigneeProp = new StringProperty(ASSIGNEE, EMPTY_STRING);
  private final ArrayProperty<StringValue> candidateGroupsListProp =
      new ArrayProperty<>(CANDIDATE_GROUPS, StringValue::new);
  private final ArrayProperty<StringValue> candidateUsersListProp =
      new ArrayProperty<>(CANDIDATE_USERS, StringValue::new);
  private final StringProperty dueDateProp = new StringProperty(DUE_DATE, EMPTY_STRING);
  private final StringProperty followUpDateProp = new StringProperty(FOLLOW_UP_DATE, EMPTY_STRING);
  private final LongProperty formKeyProp = new LongProperty("formKey", -1);
  private final StringProperty externalFormReferenceProp =
      new StringProperty("externalFormReference", EMPTY_STRING);

  private final DocumentProperty variableProp = new DocumentProperty("variables");
  private final PackedProperty customHeadersProp = new PackedProperty("customHeaders", NO_HEADERS);

  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROP_PROCESS_INSTANCE_KEY, -1L);
  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_PROCESS_BPMN_PROCESS_ID, EMPTY_STRING);
  private final IntegerProperty processDefinitionVersionProp =
      new IntegerProperty("processDefinitionVersion", -1);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", EMPTY_STRING);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  /**
   * Tracks the names of user task attributes that are intended to be modified (e.g. on `UPDATE`),
   * or have been modified (e.g. on `UPDATED` or `CORRECTED`).
   *
   * <p>Since our `msgpack` implementation doesn't support `null` values, all record attributes have
   * default non-null values. As a result, without `changedAttributes`, it would be impossible to
   * determine whether an attribute was explicitly modified or simply retained its default value.
   * This property solves that issue by explicitly listing attributes that were changed.
   *
   * <p>The content of `changedAttributes` field depend on the type of record:
   *
   * <ul>
   *   <li><b>UPDATE command:</b> Specifies which attributes are intended to be updated, allowing
   *       explicit changes (including clearing values to default). The presence of an attribute in
   *       this list indicates that it was explicitly modified by the update request.
   *   <li><b>UPDATING and UPDATED events:</b> Contains only attributes whose values differ from the
   *       persisted state. In the case of `UPDATED` this can include any corrections made by
   *       listeners, unless they corrected them back to the prior state.
   *   <li><b>CORRECTED event:</b> Contains only attributes that were modified by a listener,
   *       comparing them to the previous event that triggered the listener or a prior listener
   *       correction. It doesn't include the original attributes that caused the listener to
   *       trigger.
   * </ul>
   *
   * <p>For `CORRECTED` events, this list reflects only attributes that were actually changed by the
   * correction. If a listener applies the same correction multiple times, subsequent `CORRECTED`
   * events will not contain duplicate changes.
   *
   * @see #wrapChangedAttributes(UserTaskRecord, boolean)
   * @see #wrapChangedAttributesIfValueChanged(UserTaskRecord)
   * @see #correctAttributes(List, JobResultCorrections)
   */
  private final ArrayProperty<StringValue> changedAttributesProp =
      new ArrayProperty<>("changedAttributes", StringValue::new);

  private final StringProperty actionProp = new StringProperty("action", EMPTY_STRING);
  private final LongProperty creationTimestampProp = new LongProperty("creationTimestamp", -1L);
  private final IntegerProperty priorityProp = new IntegerProperty(PRIORITY, 50);
  private final StringProperty deniedReasonProp = new StringProperty("deniedReason", EMPTY_STRING);

  public UserTaskRecord() {
    super(22);
    declareProperty(userTaskKeyProp)
        .declareProperty(assigneeProp)
        .declareProperty(candidateGroupsListProp)
        .declareProperty(candidateUsersListProp)
        .declareProperty(dueDateProp)
        .declareProperty(followUpDateProp)
        .declareProperty(formKeyProp)
        .declareProperty(externalFormReferenceProp)
        .declareProperty(variableProp)
        .declareProperty(customHeadersProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionVersionProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(changedAttributesProp)
        .declareProperty(actionProp)
        .declareProperty(creationTimestampProp)
        .declareProperty(priorityProp)
        .declareProperty(deniedReasonProp);
  }

  /** Like {@link #wrap(UserTaskRecord)} but does not set the variables. */
  public void wrapWithoutVariables(final UserTaskRecord record) {
    userTaskKeyProp.setValue(record.getUserTaskKey());
    assigneeProp.setValue(record.getAssigneeBuffer());
    setCandidateGroupsList(record.getCandidateGroupsList());
    setCandidateUsersList(record.getCandidateUsersList());
    dueDateProp.setValue(record.getDueDateBuffer());
    followUpDateProp.setValue(record.getFollowUpDateBuffer());
    formKeyProp.setValue(record.getFormKey());
    externalFormReferenceProp.setValue(record.getExternalFormReferenceBuffer());
    final DirectBuffer customHeaders = record.getCustomHeadersBuffer();
    customHeadersProp.setValue(customHeaders, 0, customHeaders.capacity());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    processDefinitionVersionProp.setValue(record.getProcessDefinitionVersion());
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    elementIdProp.setValue(record.getElementIdBuffer());
    elementInstanceKeyProp.setValue(record.getElementInstanceKey());
    tenantIdProp.setValue(record.getTenantIdBuffer());
    creationTimestampProp.setValue(record.getCreationTimestamp());
    setChangedAttributesProp(record.getChangedAttributesProp());
    actionProp.setValue(record.getActionBuffer());
    priorityProp.setValue(record.getPriority());
    deniedReasonProp.setValue(record.getDeniedReason());
  }

  /**
   * Wraps the given record's properties, typically used to quickly set the same properties.
   *
   * @implNote This method uses variable assignment. So changing a non-primitive in one record also
   *     affects the other. If you need to separate the records, use {@link #copy()} instead.
   */
  public void wrap(final UserTaskRecord record) {
    wrapWithoutVariables(record);
    variableProp.setValue(record.getVariablesBuffer());
  }

  /** Returns a full copy of the record. */
  public UserTaskRecord copy() {
    final UserTaskRecord copy = new UserTaskRecord();
    copy.copyFrom(this);
    return copy;
  }

  /**
   * Updates the attributes of this {@link UserTaskRecord} based on the given record.
   *
   * @apiNote If {@code includeTrackingProperties} is {@code true}, all attributes in the given
   *     record's `changedAttributes` list will be added to this record's `changedAttributesProp`,
   *     even if their values match the existing values. To update attributes and track only truly
   *     changed attributes, use the {@link #wrapChangedAttributesIfValueChanged} method instead.
   * @param record the record containing the changed attributes list and new attribute values
   * @param includeTrackingProperties whether to include all changed attributes in the tracking list
   */
  public void wrapChangedAttributes(
      final UserTaskRecord record, final boolean includeTrackingProperties) {
    if (includeTrackingProperties) {
      changedAttributesProp.reset();
    }

    record
        .getChangedAttributes()
        .forEach(
            attribute -> {
              updateAttribute(attribute, record);
              if (includeTrackingProperties) {
                addChangedAttribute(attribute);
              }
            });
  }

  /**
   * Updates the attributes of this {@link UserTaskRecord} based on the given record and adds the
   * attribute to `changedAttributesProp` only if its value was actually changed.
   *
   * @param record the record containing the changed attributes list and new attribute values
   */
  public void wrapChangedAttributesIfValueChanged(final UserTaskRecord record) {
    changedAttributesProp.reset();

    record.getChangedAttributes().stream()
        .filter(attribute -> isAttributeValueChanged(attribute, record))
        .forEach(
            attribute -> {
              updateAttribute(attribute, record);
              addChangedAttribute(attribute);
            });
  }

  private void updateAttribute(final String attributeName, final UserTaskRecord record) {
    switch (attributeName) {
      case ASSIGNEE:
        setAssignee(record.getAssigneeBuffer());
        break;
      case CANDIDATE_GROUPS:
        setCandidateGroupsList(record.getCandidateGroupsList());
        break;
      case CANDIDATE_USERS:
        setCandidateUsersList(record.getCandidateUsersList());
        break;
      case DUE_DATE:
        dueDateProp.setValue(record.getDueDateBuffer());
        break;
      case FOLLOW_UP_DATE:
        followUpDateProp.setValue(record.getFollowUpDateBuffer());
        break;
      case PRIORITY:
        priorityProp.setValue(record.getPriority());
        break;
      default:
        break;
    }
  }

  /**
   * Corrects those attributes of the user task record provided in the list of corrected attributes
   * with the values from the given corrections. Corrected attributes are tracked as changed
   * attributes.
   *
   * @param correctedAttributes the attributes to correct
   * @param corrections the corrections to apply
   */
  public void correctAttributes(
      final List<String> correctedAttributes, final JobResultCorrections corrections) {
    changedAttributesProp.reset();

    correctedAttributes.forEach(
        attribute -> {
          switch (attribute) {
            case ASSIGNEE -> setAssignee(corrections.getAssignee());
            case CANDIDATE_GROUPS -> setCandidateGroupsList(corrections.getCandidateGroupsList());
            case CANDIDATE_USERS -> setCandidateUsersList(corrections.getCandidateUsersList());
            case DUE_DATE -> setDueDate(corrections.getDueDate());
            case FOLLOW_UP_DATE -> setFollowUpDate(corrections.getFollowUpDate());
            case PRIORITY -> setPriority(corrections.getPriority());
            default ->
                throw new IllegalArgumentException("Unknown corrected attribute: " + attribute);
          }
          addChangedAttribute(attribute);
        });
  }

  @Override
  public long getUserTaskKey() {
    return userTaskKeyProp.getValue();
  }

  @Override
  public String getAssignee() {
    return bufferAsString(assigneeProp.getValue());
  }

  @Override
  public List<String> getCandidateGroupsList() {
    return StreamSupport.stream(candidateGroupsListProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  @Override
  public List<String> getCandidateUsersList() {
    return StreamSupport.stream(candidateUsersListProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  @Override
  public String getDueDate() {
    return bufferAsString(dueDateProp.getValue());
  }

  @Override
  public String getFollowUpDate() {
    return bufferAsString(followUpDateProp.getValue());
  }

  @Override
  public long getFormKey() {
    return formKeyProp.getValue();
  }

  @Override
  public List<String> getChangedAttributes() {
    return StreamSupport.stream(changedAttributesProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  @Override
  public String getAction() {
    return bufferAsString(actionProp.getValue());
  }

  @Override
  public String getExternalFormReference() {
    return bufferAsString(externalFormReferenceProp.getValue());
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return MsgPackConverter.convertToStringMap(customHeadersProp.getValue());
  }

  @Override
  public long getCreationTimestamp() {
    return creationTimestampProp.getValue();
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProp.getValue());
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersionProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public UserTaskRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public int getPriority() {
    return priorityProp.getValue();
  }

  public UserTaskRecord setPriority(final int priority) {
    priorityProp.setValue(priority);
    return this;
  }

  public UserTaskRecord setProcessDefinitionVersion(final int version) {
    processDefinitionVersionProp.setValue(version);
    return this;
  }

  public UserTaskRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public UserTaskRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public UserTaskRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public UserTaskRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public UserTaskRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public UserTaskRecord setCreationTimestamp(final long creationTimestamp) {
    creationTimestampProp.setValue(creationTimestamp);
    return this;
  }

  public UserTaskRecord setCustomHeaders(final DirectBuffer buffer) {
    customHeadersProp.setValue(buffer, 0, buffer.capacity());
    return this;
  }

  public UserTaskRecord setExternalFormReference(final DirectBuffer externalFormReference) {
    externalFormReferenceProp.setValue(externalFormReference);
    return this;
  }

  public UserTaskRecord setExternalFormReference(final String externalFormReference) {
    externalFormReferenceProp.setValue(externalFormReference);
    return this;
  }

  public UserTaskRecord setAction(final String action) {
    actionProp.setValue(action);
    return this;
  }

  public UserTaskRecord setAction(final DirectBuffer action) {
    actionProp.setValue(action);
    return this;
  }

  public UserTaskRecord setChangedAttributes(final List<String> changedAttributes) {
    changedAttributesProp.reset();
    changedAttributes.forEach(this::addChangedAttribute);
    return this;
  }

  public UserTaskRecord setFormKey(final long formKey) {
    formKeyProp.setValue(formKey);
    return this;
  }

  public UserTaskRecord setFollowUpDate(final String followUpDate) {
    followUpDateProp.setValue(followUpDate);
    return this;
  }

  public UserTaskRecord setFollowUpDate(final DirectBuffer followUpDate) {
    followUpDateProp.setValue(followUpDate);
    return this;
  }

  public UserTaskRecord setDueDate(final String dueDate) {
    dueDateProp.setValue(dueDate);
    return this;
  }

  public UserTaskRecord setDueDate(final DirectBuffer dueDate) {
    dueDateProp.setValue(dueDate);
    return this;
  }

  public UserTaskRecord setCandidateUsersList(final List<String> candidateUsers) {
    candidateUsersListProp.reset();
    candidateUsers.forEach(
        tenantId -> candidateUsersListProp.add().wrap(BufferUtil.wrapString(tenantId)));
    return this;
  }

  public UserTaskRecord setCandidateGroupsList(final List<String> candidateGroups) {
    candidateGroupsListProp.reset();
    candidateGroups.forEach(
        tenantId -> candidateGroupsListProp.add().wrap(BufferUtil.wrapString(tenantId)));
    return this;
  }

  public UserTaskRecord setAssignee(final String assignee) {
    assigneeProp.setValue(assignee);
    return this;
  }

  public UserTaskRecord setAssignee(final DirectBuffer assignee) {
    assigneeProp.setValue(assignee);
    return this;
  }

  public UserTaskRecord setUserTaskKey(final long userTaskKey) {
    userTaskKeyProp.setValue(userTaskKey);
    return this;
  }

  public String getDeniedReason() {
    return bufferAsString(deniedReasonProp.getValue());
  }

  public UserTaskRecord setDeniedReason(final String deniedReason) {
    deniedReasonProp.setValue(deniedReason);
    return this;
  }

  public UserTaskRecord setAssigneeChanged() {
    changedAttributesProp.add().wrap(ASSIGNEE_VALUE);
    return this;
  }

  public UserTaskRecord setCandidateGroupsChanged() {
    changedAttributesProp.add().wrap(CANDIDATE_GROUPS_VALUE);
    return this;
  }

  public UserTaskRecord setCandidateUsersChanged() {
    changedAttributesProp.add().wrap(CANDIDATE_USERS_VALUE);
    return this;
  }

  public UserTaskRecord setDueDateChanged() {
    changedAttributesProp.add().wrap(DUE_DATE_VALUE);
    return this;
  }

  public UserTaskRecord setFollowUpDateChanged() {
    changedAttributesProp.add().wrap(FOLLOW_UP_DATE_VALUE);
    return this;
  }

  public UserTaskRecord setPriorityChanged() {
    changedAttributesProp.add().wrap(PRIORITY_VALUE);
    return this;
  }

  public UserTaskRecord setVariablesChanged() {
    changedAttributesProp.add().wrap(VARIABLES_VALUE);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public UserTaskRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variableProp.getValue());
  }

  public UserTaskRecord setVariables(final DirectBuffer variables) {
    variableProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCustomHeadersBuffer() {
    return customHeadersProp.getValue();
  }

  @JsonIgnore
  public ArrayProperty<StringValue> getChangedAttributesProp() {
    return changedAttributesProp;
  }

  public UserTaskRecord setChangedAttributesProp(
      final ArrayProperty<StringValue> changedAttributes) {
    changedAttributesProp.reset();
    changedAttributes.forEach(attribute -> changedAttributesProp.add().wrap(attribute));
    return this;
  }

  public UserTaskRecord addChangedAttribute(final String attribute) {
    changedAttributesProp.add().wrap(BufferUtil.wrapString(attribute));
    return this;
  }

  public void setDiffAsChangedAttributes(final UserTaskRecord other) {
    changedAttributesProp.reset();
    determineChangedAttributes(other).forEach(this::addChangedAttribute);
  }

  /**
   * Determines which attributes have changed between this {@link UserTaskRecord} and another
   * instance.
   *
   * <p>This method compares all trackable user task attributes and returns a list of attribute
   * names that have different values between the two records.
   *
   * @param other the {@link UserTaskRecord} to compare against
   * @return a list of attribute names that have changed
   * @implNote Attributes are compared using {@link UserTaskRecord#ATTRIBUTE_GETTER_MAP}, ensuring
   *     that all supported fields are checked dynamically.
   */
  public List<String> determineChangedAttributes(final UserTaskRecord other) {
    return ATTRIBUTE_GETTER_MAP.keySet().stream()
        .sorted()
        .filter(attribute -> isAttributeValueChanged(attribute, other))
        .toList();
  }

  public boolean hasChangedAttributes() {
    return !changedAttributesProp.isEmpty();
  }

  private boolean isAttributeValueChanged(final String attribute, final UserTaskRecord other) {
    final var attributeGetter = ATTRIBUTE_GETTER_MAP.get(attribute);
    if (attributeGetter == null) {
      return false;
    }

    final var thisAttributeValue = attributeGetter.apply(this);
    final var otherAttributeValue = attributeGetter.apply(other);
    return !Objects.equals(thisAttributeValue, otherAttributeValue);
  }

  @JsonIgnore
  public DirectBuffer getAssigneeBuffer() {
    return assigneeProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDueDateBuffer() {
    return dueDateProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getFollowUpDateBuffer() {
    return followUpDateProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variableProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getExternalFormReferenceBuffer() {
    return externalFormReferenceProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getActionBuffer() {
    return actionProp.getValue();
  }

  @JsonIgnore
  public String getActionOrDefault(final String defaultAction) {
    final String action = bufferAsString(actionProp.getValue());
    return action.isEmpty() ? defaultAction : action;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public UserTaskRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  public UserTaskRecord unsetAssignee() {
    assigneeProp.setValue(EMPTY_STRING);
    final var changedAttributes = getChangedAttributes();
    changedAttributes.remove(ASSIGNEE);
    setChangedAttributes(changedAttributes);
    return this;
  }
}
