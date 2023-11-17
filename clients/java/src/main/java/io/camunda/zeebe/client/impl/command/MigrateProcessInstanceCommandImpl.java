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

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.MigrateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.MigrateProcessInstanceCommandStep1.MigrateProcessInstanceCommandFinalStep;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.api.response.MigrateProcessInstanceResponse;
import java.time.Duration;

public final class MigrateProcessInstanceCommandImpl
    implements MigrateProcessInstanceCommandStep1, MigrateProcessInstanceCommandFinalStep {
  private final long processInstanceKey;
  private Duration requestTimeout;

  public MigrateProcessInstanceCommandImpl(
      final long processInstanceKey, final Duration requestTimeout) {
    this.processInstanceKey = processInstanceKey;
    this.requestTimeout = requestTimeout;
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep migrationPlan(
      final long targetProcessDefinitionKey) {
    // TODO - will be implemented with https://github.com/camunda/zeebe/issues/14921
    return this;
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep migrationPlan(final MigrationPlan migrationPlan) {
    // TODO - will be implemented with https://github.com/camunda/zeebe/issues/14921
    return this;
  }

  @Override
  public MigrateProcessInstanceCommandFinalStep addMappingInstruction(
      final String sourceElementId, final String targetElementId) {
    // TODO - will be implemented with https://github.com/camunda/zeebe/issues/14921
    return this;
  }

  @Override
  public FinalCommandStep<MigrateProcessInstanceResponse> requestTimeout(
      final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<MigrateProcessInstanceResponse> send() {
    // TODO - will be implemented with https://github.com/camunda/zeebe/issues/14921
    throw new UnsupportedOperationException(
        "Migrating process instance is not supported in Java Client yet.");
  }
}
