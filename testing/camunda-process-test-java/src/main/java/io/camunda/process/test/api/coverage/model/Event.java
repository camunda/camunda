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
package io.camunda.process.test.api.coverage.model;

import java.time.Instant;

/**
 * An event is a recording of a specific action that happened in the engine.
 *
 * <p>Source of the event (flow node, sequence flow or dmn rule). Type of the event. Definition key
 * of the element where the event happened. Type of the event's element. Key of the model in which
 * the event happened (process definition key). Timestamp when the event happened.
 */
public class Event {

  private final EventSource source;
  private final EventType type;
  private final String definitionKey;
  private final String elementType;
  private final String modelKey;
  private final long timestamp;

  public Event(
      final EventSource source,
      final EventType type,
      final String definitionKey,
      final String elementType,
      final String modelKey) {
    this(source, type, definitionKey, elementType, modelKey, Instant.now().getEpochSecond());
  }

  public Event(
      final EventSource source,
      final EventType type,
      final String definitionKey,
      final String elementType,
      final String modelKey,
      final long timestamp) {
    this.source = source;
    this.type = type;
    this.definitionKey = definitionKey;
    this.elementType = elementType;
    this.modelKey = modelKey;
    this.timestamp = timestamp;
  }

  public EventSource getSource() {
    return source;
  }

  public EventType getType() {
    return type;
  }

  public String getDefinitionKey() {
    return definitionKey;
  }

  public String getElementType() {
    return elementType;
  }

  public String getModelKey() {
    return modelKey;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
