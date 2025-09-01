package io.camunda.zeebe.gateway.validation.runtime;

import java.util.Map;

/**
 * Optional interface that payload objects can implement to expose original JSON token kinds
 * captured during parsing. Keys are property names; values are token kind identifiers
 * such as STRING, NUMBER, BOOLEAN, OBJECT, ARRAY, NULL.
 */
public interface RawTokenCarrier {
  Map<String, String> getTokenKinds();
}
