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
package io.zeebe.example.data;

import java.util.Scanner;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.subscription.JobHandler;

public class HandlePayloadAsPojo
{
    public static void main(String[] args)
    {
        final String broker = "127.0.0.1:51015";
        final String topic = "default-topic";

        final ZeebeClientBuilder builder = ZeebeClient
            .newClientBuilder()
            .brokerContactPoint(broker);

        try (ZeebeClient client = builder.build())
        {
            final WorkflowClient workflowClient = client.topicClient(topic).workflowClient();
            final JobClient jobClient = client.topicClient(topic).jobClient();

            final Order order = new Order();
            order.setOrderId(31243);

            workflowClient
                .newCreateInstanceCommand()
                .bpmnProcessId("demoProcess")
                .latestVersion()
                .payload(order)
                .send()
                .join();

            jobClient
                .newWorker()
                .jobType("foo")
                .handler(new DemoJobHandler())
                .open();

            // run until System.in receives exit command
            waitUntilSystemInput("exit");
        }
    }

    private static class DemoJobHandler implements JobHandler
    {
        @Override
        public void handle(JobClient client, JobEvent job)
        {
            // read the payload of the job
            final Order order = job.getPayloadAsType(Order.class);
            System.out.println("new job with orderId: " + order.getOrderId());

            // update the payload and complete the job
            order.setTotalPrice(46.50);

            client.newCompleteCommand(job)
                .payload(order)
                .send();
        }
    }

    public static class Order
    {
        private long orderId;
        private double totalPrice;

        public long getOrderId()
        {
            return orderId;
        }

        public void setOrderId(long orderId)
        {
            this.orderId = orderId;
        }

        public double getTotalPrice()
        {
            return totalPrice;
        }

        public void setTotalPrice(double totalPrice)
        {
            this.totalPrice = totalPrice;
        }
    }

    private static void waitUntilSystemInput(final String exitCode)
    {
        try (Scanner scanner = new Scanner(System.in))
        {
            while (scanner.hasNextLine())
            {
                final String nextLine = scanner.nextLine();
                if (nextLine.contains(exitCode))
                {
                    return;
                }
            }
        }
    }
}
