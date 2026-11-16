/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.usertask;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class UserTaskRecord extends MsgpackBufferValue<UserTaskRecord>
    implements MsgpackRecordValue, UserTaskRecordValue {

  public static final DirectBuffer NO_HEADERS = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

  public static final String ASSIGNEE = "assignee";
  public static final String CANDIDATE_GROUPS = "candidateGroupsList";
  public static final String CANDIDATE_USERS = "candidateUsersList";
  public static final String DUE_DATE = "dueDate";
  public static final String FOLLOW_UP_DATE = "followUpDate";
  public static final String PRIORITY = "priority";
  public static final String VARIABLES = "variables";

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

  long userTaskKey = -1;
  DirectBuffer assignee = Decoding.EMPTY;
  final List<DirectBuffer> candidateGroupsList = new ArrayList<>();
  final List<DirectBuffer> candidateUsersList = new ArrayList<>();
  DirectBuffer dueDate = Decoding.EMPTY;
  DirectBuffer followUpDate = Decoding.EMPTY;
  long formKey = -1;
  DirectBuffer externalFormReference = Decoding.EMPTY;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackProperty(raw = true)
  DirectBuffer customHeaders = NO_HEADERS;

  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  int processDefinitionVersion = -1;
  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  DirectBuffer elementId = Decoding.EMPTY;
  long elementInstanceKey = -1L;
  DirectBuffer tenantId = wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

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
  final List<DirectBuffer> changedAttributes = new ArrayList<>();

  DirectBuffer action = Decoding.EMPTY;
  long creationTimestamp = -1L;
  int priority = 50;
  DirectBuffer deniedReason = Decoding.EMPTY;
  final List<DirectBuffer> tags = new ArrayList<>();
  long listenersConfigKey = -1L;
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer businessId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /** Like {@link #wrap(UserTaskRecord)} but does not set the variables. */
  public void wrapWithoutVariables(final UserTaskRecord record) {
    userTaskKey = record.getUserTaskKey();
    assignee = record.getAssigneeBuffer();
    setCandidateGroupsList(record.getCandidateGroupsList());
    setCandidateUsersList(record.getCandidateUsersList());
    dueDate = record.getDueDateBuffer();
    followUpDate = record.getFollowUpDateBuffer();
    formKey = record.getFormKey();
    externalFormReference = record.getExternalFormReferenceBuffer();
    customHeaders = record.getCustomHeadersBuffer();
    bpmnProcessId = record.getBpmnProcessIdBuffer();
    processDefinitionVersion = record.getProcessDefinitionVersion();
    processDefinitionKey = record.getProcessDefinitionKey();
    processInstanceKey = record.getProcessInstanceKey();
    elementId = record.getElementIdBuffer();
    elementInstanceKey = record.getElementInstanceKey();
    tenantId = record.getTenantIdBuffer();
    creationTimestamp = record.getCreationTimestamp();
    setChangedAttributesProp(record.getChangedAttributesProp());
    action = record.getActionBuffer();
    priority = record.getPriority();
    deniedReason = wrapString(record.getDeniedReason());
    setTags(record.getTags());
    listenersConfigKey = record.getListenersConfigKey();
    rootProcessInstanceKey = record.getRootProcessInstanceKey();
    storageOrdinal = record.getStorageOrdinal();
    businessId = record.getBusinessIdBuffer();
  }

  /**
   * Wraps the given record's properties, typically used to quickly set the same properties.
   *
   * @implNote This method uses variable assignment. So changing a non-primitive in one record also
   *     affects the other. If you need to separate the records, use {@link #copy()} instead.
   */
  public void wrap(final UserTaskRecord record) {
    wrapWithoutVariables(record);
    variables = record.getVariablesBuffer();
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
   *     record's `changedAttributes` list will be added to this record's `changedAttributes`, even
   *     if their values match the existing values. To update attributes and track only truly
   *     changed attributes, use the {@link #wrapChangedAttributesIfValueChanged} method instead.
   * @param record the record containing the changed attributes list and new attribute values
   * @param includeTrackingProperties whether to include all changed attributes in the tracking list
   */
  public void wrapChangedAttributes(
      final UserTaskRecord record, final boolean includeTrackingProperties) {
    if (includeTrackingProperties) {
      changedAttributes.clear();
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
   * attribute to `changedAttributes` only if its value was actually changed.
   *
   * @param record the record containing the changed attributes list and new attribute values
   */
  public void wrapChangedAttributesIfValueChanged(final UserTaskRecord record) {
    changedAttributes.clear();

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
        dueDate = record.getDueDateBuffer();
        break;
      case FOLLOW_UP_DATE:
        followUpDate = record.getFollowUpDateBuffer();
        break;
      case PRIORITY:
        priority = record.getPriority();
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
    changedAttributes.clear();

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
    return userTaskKey;
  }

  @Override
  public String getAssignee() {
    return bufferAsString(assignee);
  }

  @Override
  public List<String> getCandidateGroupsList() {
    return toStringList(candidateGroupsList);
  }

  @Override
  public List<String> getCandidateUsersList() {
    return toStringList(candidateUsersList);
  }

  @Override
  public String getDueDate() {
    return bufferAsString(dueDate);
  }

  @Override
  public String getFollowUpDate() {
    return bufferAsString(followUpDate);
  }

  @Override
  public long getFormKey() {
    return formKey;
  }

  @Override
  public List<String> getChangedAttributes() {
    return toStringList(changedAttributes);
  }

  @Override
  public String getAction() {
    return bufferAsString(action);
  }

  @Override
  public String getExternalFormReference() {
    return bufferAsString(externalFormReference);
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return MsgPackConverter.convertToStringMap(customHeaders);
  }

  @Override
  public long getCreationTimestamp() {
    return creationTimestamp;
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementId);
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public UserTaskRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  public UserTaskRecord setPriority(final int priority) {
    this.priority = priority;
    return this;
  }

  @Override
  public Set<String> getTags() {
    return tags.stream().map(BufferUtil::bufferAsString).collect(Collectors.toSet());
  }

  public UserTaskRecord setTags(final Set<String> tags) {
    this.tags.clear();
    if (tags != null) {
      tags.forEach(tag -> this.tags.add(wrapString(tag)));
    }
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public UserTaskRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public UserTaskRecord setBusinessId(final String businessId) {
    this.businessId = wrapString(businessId);
    return this;
  }

  public UserTaskRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  public UserTaskRecord setProcessDefinitionVersion(final int version) {
    processDefinitionVersion = version;
    return this;
  }

  public UserTaskRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = wrapString(bpmnProcessId);
    return this;
  }

  public UserTaskRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public UserTaskRecord setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public UserTaskRecord setElementId(final String elementId) {
    this.elementId = wrapString(elementId);
    return this;
  }

  public UserTaskRecord setElementId(final DirectBuffer elementId) {
    this.elementId = elementId;
    return this;
  }

  public UserTaskRecord setCreationTimestamp(final long creationTimestamp) {
    this.creationTimestamp = creationTimestamp;
    return this;
  }

  public UserTaskRecord setCustomHeaders(final DirectBuffer buffer) {
    customHeaders = buffer;
    return this;
  }

  public UserTaskRecord setExternalFormReference(final DirectBuffer externalFormReference) {
    this.externalFormReference = externalFormReference;
    return this;
  }

  public UserTaskRecord setExternalFormReference(final String externalFormReference) {
    this.externalFormReference = wrapString(externalFormReference);
    return this;
  }

  public UserTaskRecord setAction(final String action) {
    this.action = wrapString(action);
    return this;
  }

  public UserTaskRecord setAction(final DirectBuffer action) {
    this.action = action;
    return this;
  }

  public UserTaskRecord setChangedAttributes(final List<String> changedAttributes) {
    this.changedAttributes.clear();
    changedAttributes.forEach(this::addChangedAttribute);
    return this;
  }

  public UserTaskRecord setFormKey(final long formKey) {
    this.formKey = formKey;
    return this;
  }

  public UserTaskRecord setFollowUpDate(final String followUpDate) {
    this.followUpDate = wrapString(followUpDate);
    return this;
  }

  public UserTaskRecord setFollowUpDate(final DirectBuffer followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public UserTaskRecord setDueDate(final String dueDate) {
    this.dueDate = wrapString(dueDate);
    return this;
  }

  public UserTaskRecord setDueDate(final DirectBuffer dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public UserTaskRecord setCandidateUsersList(final List<String> candidateUsers) {
    candidateUsersList.clear();
    candidateUsers.forEach(user -> candidateUsersList.add(wrapString(user)));
    return this;
  }

  public UserTaskRecord setCandidateGroupsList(final List<String> candidateGroups) {
    candidateGroupsList.clear();
    candidateGroups.forEach(group -> candidateGroupsList.add(wrapString(group)));
    return this;
  }

  public UserTaskRecord setAssignee(final String assignee) {
    this.assignee = wrapString(assignee);
    return this;
  }

  public UserTaskRecord setAssignee(final DirectBuffer assignee) {
    this.assignee = assignee;
    return this;
  }

  public UserTaskRecord setUserTaskKey(final long userTaskKey) {
    this.userTaskKey = userTaskKey;
    return this;
  }

  public long getListenersConfigKey() {
    return listenersConfigKey;
  }

  public UserTaskRecord setListenersConfigKey(final long listenersConfigKey) {
    this.listenersConfigKey = listenersConfigKey;
    return this;
  }

  public String getDeniedReason() {
    return bufferAsString(deniedReason);
  }

  public UserTaskRecord setDeniedReason(final String deniedReason) {
    this.deniedReason = wrapString(deniedReason);
    return this;
  }

  public UserTaskRecord setAssigneeChanged() {
    return addChangedAttribute(ASSIGNEE);
  }

  public UserTaskRecord setCandidateGroupsChanged() {
    return addChangedAttribute(CANDIDATE_GROUPS);
  }

  public UserTaskRecord setCandidateUsersChanged() {
    return addChangedAttribute(CANDIDATE_USERS);
  }

  public UserTaskRecord setDueDateChanged() {
    return addChangedAttribute(DUE_DATE);
  }

  public UserTaskRecord setFollowUpDateChanged() {
    return addChangedAttribute(FOLLOW_UP_DATE);
  }

  public UserTaskRecord setPriorityChanged() {
    return addChangedAttribute(PRIORITY);
  }

  public UserTaskRecord setVariablesChanged() {
    return addChangedAttribute(VARIABLES);
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public UserTaskRecord setTenantId(final String tenantId) {
    this.tenantId = wrapString(tenantId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public UserTaskRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCustomHeadersBuffer() {
    return customHeaders;
  }

  @JsonIgnore
  public List<DirectBuffer> getChangedAttributesProp() {
    return changedAttributes;
  }

  public UserTaskRecord setChangedAttributesProp(final List<DirectBuffer> changedAttributes) {
    this.changedAttributes.clear();
    this.changedAttributes.addAll(changedAttributes);
    return this;
  }

  public UserTaskRecord addChangedAttribute(final String attribute) {
    changedAttributes.add(wrapString(attribute));
    return this;
  }

  public void setDiffAsChangedAttributes(final UserTaskRecord other) {
    changedAttributes.clear();
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
    return !changedAttributes.isEmpty();
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
    return assignee;
  }

  @JsonIgnore
  public DirectBuffer getDueDateBuffer() {
    return dueDate;
  }

  @JsonIgnore
  public DirectBuffer getFollowUpDateBuffer() {
    return followUpDate;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementId;
  }

  @JsonIgnore
  public DirectBuffer getExternalFormReferenceBuffer() {
    return externalFormReference;
  }

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return tenantId;
  }

  @JsonIgnore
  public DirectBuffer getActionBuffer() {
    return action;
  }

  @JsonIgnore
  public String getActionOrDefault(final String defaultAction) {
    final String actionString = bufferAsString(action);
    return actionString.isEmpty() ? defaultAction : actionString;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public UserTaskRecord setProcessInstanceKey(final long key) {
    processInstanceKey = key;
    return this;
  }

  public UserTaskRecord unsetAssignee() {
    assignee = Decoding.EMPTY;
    final var changedAttributeNames = getChangedAttributes();
    changedAttributeNames.remove(ASSIGNEE);
    setChangedAttributes(changedAttributeNames);
    return this;
  }

  public UserTaskRecord resetChangedAttributes() {
    changedAttributes.clear();
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public UserTaskRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  private static List<String> toStringList(final List<DirectBuffer> buffers) {
    return buffers.stream().map(BufferUtil::bufferAsString).collect(Collectors.toList());
  }

  @Override
  protected MsgpackCodec<UserTaskRecord> codec() {
    return UserTaskRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<UserTaskRecord> ops() {
    return UserTaskRecordOps.INSTANCE;
  }
}
