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
package io.zeebe.client.job;

import static io.zeebe.exporter.record.Assertions.assertThat;
import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.google.common.base.Charsets;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.CreateJobResponse;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.util.TestEnvironmentRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.protocol.intent.JobIntent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CreateJobTest {

  @Rule public TestEnvironmentRule rule = new TestEnvironmentRule();

  private ZeebeClient client;

  @Before
  public void setUp() {
    client = rule.getClient();
  }

  @Test
  public void shouldCreateJob() {
    // given
    final Map<String, Object> partialCustomHeaders = new HashMap<>();
    partialCustomHeaders.put("one", 1);
    partialCustomHeaders.put("two", "II");

    // when
    final CreateJobResponse response =
        client
            .jobClient()
            .newCreateCommand()
            .jobType("testJob")
            .retries(12)
            .addCustomHeader("foo", "bar")
            .addCustomHeaders(partialCustomHeaders)
            .addCustomHeader("hello", "world")
            .send()
            .join();

    // then
    final Record<JobRecordValue> jobRecord = jobRecords(JobIntent.CREATED).getFirst();

    assertThat(jobRecord).hasKey(response.getKey());
    assertThat(jobRecord.getMetadata()).hasPartitionId(response.getPartitionId());
    assertThat(jobRecord.getValue()).hasType("testJob").hasRetries(12);
    assertThat(jobRecord.getValue().getCustomHeaders())
        .containsAllEntriesOf(partialCustomHeaders)
        .containsEntry("foo", "bar")
        .containsEntry("hello", "world");
  }

  @Test
  public void shouldCreateJobWithStringPayload() {
    // when
    final CreateJobResponse response =
        client
            .jobClient()
            .newCreateCommand()
            .jobType("testJob")
            .payload("{\"foo\": \"bar\"}")
            .send()
            .join();

    // then
    final Record<JobRecordValue> jobRecord = jobRecords(JobIntent.CREATED).getFirst();

    assertThat(jobRecord).hasKey(response.getKey());
    assertThat(jobRecord.getValue().getPayloadAsMap()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateJobWithInputStreamPayload() {
    // given
    final String payload = "{\"foo\": \"bar\"}";
    final InputStream inputStream = new ByteArrayInputStream(payload.getBytes(Charsets.UTF_8));

    // when
    final CreateJobResponse response =
        client.jobClient().newCreateCommand().jobType("testJob").payload(inputStream).send().join();

    // then
    final Record<JobRecordValue> jobRecord = jobRecords(JobIntent.CREATED).getFirst();

    assertThat(jobRecord).hasKey(response.getKey());
    assertThat(jobRecord.getValue().getPayloadAsMap()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateJobWithMapPayload() {
    // when
    final CreateJobResponse response =
        client
            .jobClient()
            .newCreateCommand()
            .jobType("testJob")
            .payload(Collections.singletonMap("foo", "bar"))
            .send()
            .join();

    // then
    final Record<JobRecordValue> jobRecord = jobRecords(JobIntent.CREATED).getFirst();

    assertThat(jobRecord).hasKey(response.getKey());
    assertThat(jobRecord.getValue().getPayloadAsMap()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateJobWithObjectPayload() {
    // when
    final CreateJobResponse response =
        client
            .jobClient()
            .newCreateCommand()
            .jobType("testJob")
            .payload(new Payload())
            .send()
            .join();

    // then
    final Record<JobRecordValue> jobRecord = jobRecords(JobIntent.CREATED).getFirst();

    assertThat(jobRecord).hasKey(response.getKey());
    assertThat(jobRecord.getValue().getPayloadAsMap()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldNotCreateJobWithNoJsonObjectAsPayload() {
    assertThatThrownBy(
            () ->
                client
                    .jobClient()
                    .newCreateCommand()
                    .jobType("testJob")
                    .payload("[]")
                    .send()
                    .join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Document has invalid format. On root level an object is only allowed.");
  }

  public static class Payload {

    private final String foo = "bar";

    Payload() {}

    public String getFoo() {
      return foo;
    }
  }
}
