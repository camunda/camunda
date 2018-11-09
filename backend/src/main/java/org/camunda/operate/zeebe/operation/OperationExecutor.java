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
package org.camunda.operate.zeebe.operation;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class OperationExecutor extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(OperationExecutor.class);

  private boolean shutdown = false;

  @Autowired
  private List<OperationHandler> handlers;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private OperateProperties operateProperties;

  public void startExecuting() {
    if (operateProperties.getOperationExecutor().isExecutorEnabled()) {
      start();
    }
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
  }

  @Override
  public void run() {
    while (!shutdown) {
      try {
        final Map<String, List<OperationEntity>> operations = executeOneBatch();

        //TODO backoff strategy
        if (operations.size() == 0) {
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {

          }
        }

      } catch (Exception ex) {
        //retry
        logger.error("Something went wrong, while executing operations batch. Will be retried. Underlying exception: ", ex.getCause());

        logger.error(ex.getMessage(), ex);

        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {

        }
      }
    }
  }

  public Map<String, List<OperationEntity>> executeOneBatch() throws PersistenceException {
    //lock the operations
    final Map<String, List<OperationEntity>> lockedOperations = batchOperationWriter.lockBatch();

    //execute all locked operations
    for (Map.Entry<String, List<OperationEntity>> wiOperations: lockedOperations.entrySet()) {
      for (OperationEntity operation: wiOperations.getValue()) {
        final OperationHandler handler = getOperationHandlers().get(operation.getType());
        if (handler == null) {
          logger.info("Operation {} on worflowInstanceId {} won't be processed, as no suitable handler was found.", operation.getType(), wiOperations.getKey());
        } else {
          handler.handle(wiOperations.getKey());
        }
      }
    }
    return lockedOperations;
  }

  @Bean
  public Map<OperationType, OperationHandler> getOperationHandlers() {
    //populate handlers map
    Map<OperationType, OperationHandler> handlerMap = new HashMap<>();
    for (OperationHandler handler: handlers) {
      handlerMap.put(handler.getType(), handler);
    }
    return handlerMap;
  }
}
