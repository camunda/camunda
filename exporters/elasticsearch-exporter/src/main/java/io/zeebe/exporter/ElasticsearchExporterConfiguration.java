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
package io.zeebe.exporter;

public class ElasticsearchExporterConfiguration {

  // elasticsearch http url
  public String url = "http://localhost:9200";

  public IndexConfiguration index = new IndexConfiguration();
  public BulkConfiguration bulk = new BulkConfiguration();

  @Override
  public String toString() {
    return "ElasticsearchExporterConfiguration{"
        + "url='"
        + url
        + '\''
        + ", index="
        + index
        + ", bulk="
        + bulk
        + '}';
  }

  public static class IndexConfiguration {
    // prefix for index and templates
    public String prefix = "zeebe-record";

    // update index template on startup
    public boolean createTemplate = true;

    // record types to export
    public boolean command = false;
    public boolean event = true;
    public boolean rejection = false;

    // value types to export
    public boolean deployment = true;
    public boolean incident = true;
    public boolean job = true;
    public boolean jobBatch = false;
    public boolean message = false;
    public boolean messageSubscription = false;
    public boolean raft = false;
    public boolean workflowInstance = true;
    public boolean workflowInstanceSubscription = false;

    @Override
    public String toString() {
      return "IndexConfiguration{"
          + "indexPrefix='"
          + prefix
          + '\''
          + ", createTemplate="
          + createTemplate
          + ", command="
          + command
          + ", event="
          + event
          + ", rejection="
          + rejection
          + ", deployment="
          + deployment
          + ", incident="
          + incident
          + ", job="
          + job
          + ", message="
          + message
          + ", messageSubscription="
          + messageSubscription
          + ", raft="
          + raft
          + ", workflowInstance="
          + workflowInstance
          + ", workflowInstanceSubscription="
          + workflowInstanceSubscription
          + '}';
    }
  }

  public static class BulkConfiguration {
    // delay before forced flush
    public int delay = 5;
    // bulk size before flush
    public int size = 1_000;

    @Override
    public String toString() {
      return "BulkConfiguration{" + "delay=" + delay + ", size=" + size + '}';
    }
  }
}
