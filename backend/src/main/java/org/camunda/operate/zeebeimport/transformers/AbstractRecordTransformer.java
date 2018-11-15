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
package org.camunda.operate.zeebeimport.transformers;

import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.IdUtil;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;

public interface AbstractRecordTransformer {

  List<OperateZeebeEntity> convert(Record record);

  default void loadEventGeneralData(Record record, EventEntity eventEntity) {
    RecordMetadata metadata = record.getMetadata();

    eventEntity.setId(IdUtil.createId(record.getPosition(), record.getMetadata().getPartitionId()));
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getMetadata().getPartitionId());
    eventEntity.setEventSourceType(EventSourceType.fromZeebeValueType(metadata.getValueType()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(record.getTimestamp()));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getMetadata().getIntent().name()));
  }
}
