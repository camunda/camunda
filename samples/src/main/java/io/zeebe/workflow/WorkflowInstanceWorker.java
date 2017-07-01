/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.workflow;

import java.time.Duration;
import java.util.Properties;
import java.util.Scanner;

import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.task.TaskSubscription;

public class WorkflowInstanceWorker
{

    public static void main(String[] args)
    {
        final String brokerContactPoint = "127.0.0.1:51015";
        final String topicName = "default-topic";
        final int partitionId = 0;
        final String taskType = "foo";
        final String lockOwner = "worker-1";

        final Properties clientProperties = new Properties();
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, brokerContactPoint);

        final ZeebeClient zeebeClient = new ZeebeClientImpl(clientProperties);

        System.out.println(String.format("> Connecting to %s ...", brokerContactPoint));
        zeebeClient.connect();

        System.out.println("> Connected.");

        System.out.println(String.format("> Open task subscription for topic '%s', partition '%d' and type '%s'", topicName, partitionId, taskType));

        final TaskSubscription subscription = zeebeClient.taskTopic(topicName, partitionId)
            .newTaskSubscription()
            .taskType(taskType)
            .lockOwner(lockOwner)
            .lockTime(Duration.ofSeconds(10))
            .handler(task ->
            {
                System.out.println(String.format(">>> [type: %s, key: %s, lockExpirationTime: %s]\n[headers: %s]\n[payload: %s]\n===",
                        task.getType(),
                        task.getKey(),
                        task.getLockExpirationTime().toString(),
                        task.getHeaders(),
                        task.getPayload()));

                task.complete();
            })
            .open();

        System.out.println("> Opened.");

        // wait for tasks
        try (Scanner scanner = new Scanner(System.in))
        {
            while (scanner.hasNextLine())
            {
                final String nextLine = scanner.nextLine();
                if (nextLine.contains("exit"))
                {
                    System.out.println("> Closing...");

                    subscription.close();
                    zeebeClient.close();

                    System.out.println("> Closed.");

                    System.exit(0);
                }
            }
        }
    }

}
