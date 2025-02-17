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
package io.camunda.zeebe.client.impl.command;

import io.camunda.client.protocol.rest.Changeset;
import io.camunda.client.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.UpdateUserTaskCommandStep1;
import io.camunda.zeebe.client.api.response.UpdateUserTaskResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class UpdateUserTaskCommandImpl implements UpdateUserTaskCommandStep1 {

  private final long userTaskKey;
  private final UserTaskUpdateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public UpdateUserTaskCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long userTaskKey) {
    this.jsonMapper = jsonMapper;
    this.userTaskKey = userTaskKey;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new UserTaskUpdateRequest();
  }

  @Override
  public FinalCommandStep<UpdateUserTaskResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<UpdateUserTaskResponse> send() {
    final HttpZeebeFuture<UpdateUserTaskResponse> result = new HttpZeebeFuture<>();
    httpClient.patch(
        "/user-tasks/" + userTaskKey,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        result);
    return result;
  }

  @Override
  public UpdateUserTaskCommandStep1 action(final String action) {
    request.setAction(action);
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 dueDate(final String dueDate) {
    ArgumentUtil.ensureNotNull("dueDate", dueDate);
    getChangesetEnsureInitialized().put(Changeset.JSON_PROPERTY_DUE_DATE, dueDate);
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 clearDueDate() {
    getChangesetEnsureInitialized().put(Changeset.JSON_PROPERTY_DUE_DATE, "");
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 followUpDate(final String followUpDate) {
    ArgumentUtil.ensureNotNull("followUpDate", followUpDate);
    getChangesetEnsureInitialized().put(Changeset.JSON_PROPERTY_FOLLOW_UP_DATE, followUpDate);
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 clearFollowUpDate() {
    getChangesetEnsureInitialized().put(Changeset.JSON_PROPERTY_FOLLOW_UP_DATE, "");
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 candidateGroups(final List<String> candidateGroups) {
    ArgumentUtil.ensureNotNull("candidateGroups", candidateGroups);
    getChangesetEnsureInitialized().put(Changeset.JSON_PROPERTY_CANDIDATE_GROUPS, candidateGroups);
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 candidateGroups(final String... candidateGroups) {
    ArgumentUtil.ensureNotNull("candidateGroups", candidateGroups);
    getChangesetEnsureInitialized()
        .put(Changeset.JSON_PROPERTY_CANDIDATE_GROUPS, Arrays.asList(candidateGroups));
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 clearCandidateGroups() {
    getChangesetEnsureInitialized()
        .put(Changeset.JSON_PROPERTY_CANDIDATE_GROUPS, Collections.emptyList());
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 candidateUsers(final List<String> candidateUsers) {
    ArgumentUtil.ensureNotNull("candidateUsers", candidateUsers);
    getChangesetEnsureInitialized().put(Changeset.JSON_PROPERTY_CANDIDATE_USERS, candidateUsers);
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 candidateUsers(final String... candidateUsers) {
    ArgumentUtil.ensureNotNull("candidateUsers", candidateUsers);
    getChangesetEnsureInitialized()
        .put(Changeset.JSON_PROPERTY_CANDIDATE_USERS, Arrays.asList(candidateUsers));
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 clearCandidateUsers() {
    getChangesetEnsureInitialized()
        .put(Changeset.JSON_PROPERTY_CANDIDATE_USERS, Collections.emptyList());
    return this;
  }

  @Override
  public UpdateUserTaskCommandStep1 priority(final Integer priority) {
    getChangesetEnsureInitialized().put(Changeset.JSON_PROPERTY_PRIORITY, priority);
    return this;
  }

  private Changeset getChangesetEnsureInitialized() {
    Changeset changeset = request.getChangeset();
    if (changeset == null) {
      changeset = new Changeset();
      request.setChangeset(changeset);
    }
    return changeset;
  }
}
