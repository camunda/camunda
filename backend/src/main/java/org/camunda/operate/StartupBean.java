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
package org.camunda.operate;

import javax.annotation.PostConstruct;
import org.camunda.operate.data.DataGenerator;
import org.camunda.operate.es.ElasticsearchSchemaManager;
import org.camunda.operate.es.archiver.Archiver;
import org.camunda.operate.zeebe.operation.OperationExecutor;
import org.camunda.operate.zeebeimport.ZeebeESImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StartupBean {

  private static final Logger logger = LoggerFactory.getLogger(StartupBean.class);

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private DataGenerator dataGenerator;

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  @Autowired
  private OperationExecutor operationExecutor;

  @Autowired
  private Archiver archiver;

  @PostConstruct
  public void initApplication() {
    logger.info("INIT: Initialize Elasticsearch schema...");
    elasticsearchSchemaManager.initializeSchema();
    logger.debug("INIT: Generate demo data...");
    try {
      dataGenerator.createZeebeDataAsync(false);
    } catch (Exception ex) {
      logger.debug("Demo data could not be generated. Cause: {}", ex.getMessage());
      logger.error("Error occurred when generating demo data.", ex);
    }
    logger.info("INIT: Start importing Zeebe data...");
    zeebeESImporter.startImportingData();
    logger.info("INIT: Start operation executor...");
    operationExecutor.startExecuting();
    logger.info("INIT: Start archiving data...");
    archiver.startArchiving();
    logger.info("INIT: DONE");
  }

}
