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
package io.zeebe.client.api.commands;

import io.zeebe.client.api.events.TopicEvent;

public interface CreateTopicCommandStep1 {
  /**
   * Set the name of the topic to create to. The name must be unique within the broker/cluster.
   *
   * @param topicName the unique name of the new topic
   * @return the builder for this command
   */
  CreateTopicCommandStep2 name(String topicName);

  interface CreateTopicCommandStep2 {
    /**
     * Set the number of partitions to create for this topic.
     *
     * @param partitions the number of partitions for this topic
     * @return the builder for this command.
     */
    CreateTopicCommandStep3 partitions(int partitions);
  }

  interface CreateTopicCommandStep3 {
    /**
     * Set the replication factor of this topic (i.e. the number of replications of a partition).
     *
     * @param replicationFactor the replication factor of this topic
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CreateTopicCommandStep4 replicationFactor(int replicationFactor);
  }

  interface CreateTopicCommandStep4 extends FinalCommandStep<TopicEvent> {
    // the place for new optional parameters
  }
}
