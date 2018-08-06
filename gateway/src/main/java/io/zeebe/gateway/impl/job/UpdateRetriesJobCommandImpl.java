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
package io.zeebe.gateway.impl.job;

import io.zeebe.gateway.api.commands.UpdateRetriesJobCommandStep1;
import io.zeebe.gateway.api.commands.UpdateRetriesJobCommandStep1.UpdateRetriesJobCommandStep2;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.impl.CommandImpl;
import io.zeebe.gateway.impl.RequestManager;
import io.zeebe.gateway.impl.command.JobCommandImpl;
import io.zeebe.gateway.impl.event.JobEventImpl;
import io.zeebe.gateway.impl.record.RecordImpl;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.EnsureUtil;

public class UpdateRetriesJobCommandImpl extends CommandImpl<JobEvent>
    implements UpdateRetriesJobCommandStep1, UpdateRetriesJobCommandStep2 {
  private final JobCommandImpl command;

  public UpdateRetriesJobCommandImpl(RequestManager commandManager, JobEvent event) {
    super(commandManager);

    EnsureUtil.ensureNotNull("base event", event);

    command = new JobCommandImpl((JobEventImpl) event, JobIntent.UPDATE_RETRIES);
  }

  @Override
  public UpdateRetriesJobCommandStep2 retries(int remaingRetries) {
    command.setRetries(remaingRetries);
    return this;
  }

  @Override
  public RecordImpl getCommand() {
    return command;
  }
}
