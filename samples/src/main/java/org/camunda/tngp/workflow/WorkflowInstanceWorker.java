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
package org.camunda.tngp.workflow;

import java.time.Duration;
import java.util.Properties;
import java.util.Scanner;

import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.client.task.TaskSubscription;

public class WorkflowInstanceWorker
{

    public static void main(String[] args)
    {
        final String brokerContactPoint = "127.0.0.1:51015";
        final String topicName = "default-topic";
        final int partitionId = 0;
        final String taskType = "foo";
        final int lockOwner = 24;

        final Properties clientProperties = new Properties();
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, brokerContactPoint);

        System.out.println(String.format("> Connecting to %s ...", brokerContactPoint));
        final TngpClient tngpClient = new TngpClientImpl(clientProperties);

        System.out.println("> Connected.");

        System.out.println(String.format("> Open task subscription for topic '%s', partition '%d' and type '%s'", topicName, partitionId, taskType));

        final TaskSubscription subscription = tngpClient.taskTopic(topicName, partitionId)
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
                    tngpClient.close();

                    System.out.println("> Closed.");

                    System.exit(0);
                }
            }
        }
    }

}
