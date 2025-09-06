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
package io.camunda.zeebe.client.impl.response;

import io.camunda.client.impl.util.ParseUtil;
import io.camunda.zeebe.client.api.response.UserTaskProperties;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class UserTaskPropertiesImpl implements UserTaskProperties {

  private final String action;
  private final String assignee;
  private final List<String> candidateGroups;
  private final List<String> candidateUsers;
  private final List<String> changedAttributes;
  private final String dueDate;
  private final String followUpDate;
  private final Long formKey;
  private final Integer priority;
  private final Long userTaskKey;

  public UserTaskPropertiesImpl(final GatewayOuterClass.UserTaskProperties props) {
    action = orNull(props::hasAction, props::getAction);
    assignee = orNull(props::hasAssignee, props::getAssignee);
    candidateGroups = props.getCandidateGroupsList();
    candidateUsers = props.getCandidateUsersList();
    changedAttributes = props.getChangedAttributesList();
    dueDate = orNull(props::hasDueDate, props::getDueDate);
    followUpDate = orNull(props::hasFollowUpDate, props::getFollowUpDate);
    formKey = orNull(props::hasFormKey, props::getFormKey);
    priority = orNull(props::hasPriority, props::getPriority);
    userTaskKey = orNull(props::hasUserTaskKey, props::getUserTaskKey);
  }

  public UserTaskPropertiesImpl(final io.camunda.client.protocol.rest.UserTaskProperties props) {
    action = props.getAction();
    assignee = props.getAssignee();
    candidateGroups = props.getCandidateGroups();
    candidateUsers = props.getCandidateUsers();
    changedAttributes = props.getChangedAttributes();
    dueDate = props.getDueDate() != null ? props.getDueDate().toString() : null;
    followUpDate = props.getFollowUpDate() != null ? props.getFollowUpDate().toString() : null;
    formKey = ParseUtil.parseLongOrNull(props.getFormKey());
    priority = props.getPriority();
    userTaskKey = ParseUtil.parseLongOrNull(props.getUserTaskKey());
  }

  @Override
  public String getAction() {
    return action;
  }

  @Override
  public String getAssignee() {
    return assignee;
  }

  @Override
  public List<String> getCandidateGroups() {
    return candidateGroups;
  }

  @Override
  public List<String> getCandidateUsers() {
    return candidateUsers;
  }

  @Override
  public List<String> getChangedAttributes() {
    return changedAttributes;
  }

  @Override
  public String getDueDate() {
    return dueDate;
  }

  @Override
  public String getFollowUpDate() {
    return followUpDate;
  }

  @Override
  public Long getFormKey() {
    return formKey;
  }

  @Override
  public Integer getPriority() {
    return priority;
  }

  @Override
  public Long getUserTaskKey() {
    return userTaskKey;
  }

  /**
   * Returns the value from the supplier if the condition is true; otherwise, returns {@code null}.
   *
   * @param hasValue a BooleanSupplier, typically referencing a {@code hasXxx()} method
   * @param getter a Supplier, typically referencing a {@code getXxx()} method
   * @param <T> the type of the value
   * @return the value from {@code getter} if {@code hasValue} returns {@code true}; otherwise
   *     {@code null}
   */
  private static <T> T orNull(final BooleanSupplier hasValue, final Supplier<T> getter) {
    return hasValue.getAsBoolean() ? getter.get() : null;
  }
}
