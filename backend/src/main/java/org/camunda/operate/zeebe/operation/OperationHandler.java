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

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.writer.PersistenceException;

public interface OperationHandler {

  void handle(String workflowInstanceId) throws PersistenceException;

  OperationType getType();

}
