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
package io.zeebe.client.impl.job;

import io.zeebe.client.api.commands.FailJobCommandStep1;
import io.zeebe.client.api.commands.FailJobCommandStep1.FailJobCommandStep2;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.impl.CommandImpl;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.client.impl.command.JobCommandImpl;
import io.zeebe.client.impl.event.JobEventImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.EnsureUtil;

public class FailJobCommandImpl extends CommandImpl<JobEvent>
    implements FailJobCommandStep1, FailJobCommandStep2 {
  private final JobCommandImpl command;

  public FailJobCommandImpl(RequestManager commandManager, JobEvent event) {
    super(commandManager);

    EnsureUtil.ensureNotNull("base event", event);

    command = new JobCommandImpl((JobEventImpl) event, JobIntent.FAIL);
  }

  @Override
  public FailJobCommandStep2 retries(int remaingRetries) {
    command.setRetries(remaingRetries);
    return this;
  }

  @Override
  public RecordImpl getCommand() {
    return command;
  }
}
