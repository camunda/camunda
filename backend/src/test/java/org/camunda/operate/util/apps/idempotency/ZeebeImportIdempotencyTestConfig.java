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
package org.camunda.operate.util.apps.idempotency;

import java.util.List;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.zeebeimport.ElasticsearchBulkProcessor;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock ElasticsearchBulkProcessor.
 */
@Configuration
public class ZeebeImportIdempotencyTestConfig {

  @Bean
  @Primary
  public CustomElasticsearchBulkProcessor elasticsearchBulkProcessor() {
    return new CustomElasticsearchBulkProcessor();
  }

  public static class CustomElasticsearchBulkProcessor extends ElasticsearchBulkProcessor {
    int attempts = 0;

    @Override
    public void persistZeebeRecords(List<? extends RecordImpl> zeebeRecords) throws PersistenceException {
      super.persistZeebeRecords(zeebeRecords);
      if (attempts < 1) {
        attempts++;
        throw new PersistenceException("Fake exception when saving data to Elasticsearch");
      }
    }

    public void cancelAttempts() {
      attempts = 0;
    }
  }

}
