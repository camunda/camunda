/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.data.generation;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static java.lang.Math.abs;

import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.zeebe.ZeebeESConstants;
import java.io.IOException;
import java.util.function.Supplier;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResultChecker {

  private static final Logger logger = LoggerFactory.getLogger(ResultChecker.class);

  private static final double PRECISION_RATE = 0.01;

  @Autowired private DataGeneratorProperties dataGeneratorProperties;

  @Autowired private RestHighLevelClient esClient;

  public void assertResults() throws IOException {
    sleepFor(1000L);
    esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
    assertProcessCount();
    assertProcessInstanceCount();
    //    assertIncidentCount();
    logger.info("Assertions passed");
  }

  private boolean assertDocsCountWithRetry(
      int lastCount, int expectedCount, Supplier<Integer> cardinalityCounter) {
    int newCount = cardinalityCounter.get();
    logger.info("Asserting amount: {}", newCount);
    if (((double) abs(newCount - expectedCount)) / expectedCount <= PRECISION_RATE) {
      return true;
    } else if (newCount > lastCount) { // data is still loading
      logger.info("Not enough. Will wait and retry");
      try {
        esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
      } catch (IOException e) {
        //
      }
      sleepFor(5000L);
      return assertDocsCountWithRetry(newCount, expectedCount, cardinalityCounter);
    } else {
      logger.info("Not enough. Will fail");
      return false;
    }
  }

  private void assertProcessCount() {
    final int expectedCount = dataGeneratorProperties.getProcessCount() + 3;
    final Supplier<Integer> processCounter =
        () -> {
          try {
            return ElasticsearchUtil.getFieldCardinality(
                esClient, getAliasName(ZeebeESConstants.PROCESS_INDEX_NAME), "value.bpmnProcessId");
          } catch (IOException e) {
            throw new DataGenerationException("Exception occurred while performing assertions", e);
          }
        };
    if (!assertDocsCountWithRetry(0, expectedCount, processCounter)) {
      throw new DataGenerationException(
          String.format(
              "Expected to have %s processes, but was %s", expectedCount, processCounter.get()));
    }
  }

  private void assertProcessInstanceCount() {
    final int expectedCount =
        dataGeneratorProperties.getProcessInstanceCount()
            + dataGeneratorProperties.getCallActivityProcessInstanceCount() * 3
            + 1;
    final Supplier<Integer> processCounter =
        () -> {
          try {
            return ElasticsearchUtil.getFieldCardinality(
                esClient,
                getAliasName(ZeebeESConstants.PROCESS_INSTANCE_INDEX_NAME),
                "value.processInstanceKey");
          } catch (IOException e) {
            throw new DataGenerationException("Exception occurred while performing assertions", e);
          }
        };
    if (!assertDocsCountWithRetry(0, expectedCount, processCounter)) {
      throw new DataGenerationException(
          String.format(
              "Expected to have %s process instances, but was %s",
              expectedCount, processCounter.get()));
    }
  }

  //  private void assertIncidentCount() {
  //    try {
  //      int processInstanceCount = getFieldCardinality(getAliasName(INCIDENT_INDEX_NAME),
  // "value.processInstanceKey");
  //      final int expectedProcessInstanceCount =
  // dataGeneratorProperties.getProcessInstanceCount();
  //      if (processInstanceCount != expectedProcessInstanceCount) {
  //        throw new DataGenerationException(String.format("Expected to have %s process instances,
  // but was %s", expectedProcessInstanceCount, processInstanceCount));
  //      }
  //    } catch (IOException ex) {
  //      throw new DataGenerationException("Exception occurred while performing assertions", ex);
  //    }
  //  }

  public String getAliasName(String name) {
    return String.format("%s-%s", dataGeneratorProperties.getZeebeElasticsearchPrefix(), name);
  }
}
