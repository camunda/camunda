/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.importing.variable;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Accessors(chain = true)
public class PluginVariableDto {

  /**
   * The id of the variable.
   *
   * Note: This field is required in order to be imported to Optimize.
   * Also the id must be unique. Otherwise the variable might not
   * be imported at all.
   */
  private String id;

  /**
   * The name of the variable.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String name;

  /**
   * The type of the variable. This can be all primitive types that are supported by the engine.
   * In particular, String, Integer, Long, Short, Double, Boolean, Date.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String type;

  /**
   * The value of the variable.
   */
  private String value;

  /**
   * The timestamp of the last update to the variable.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private OffsetDateTime timestamp;

  /**
   * A map containing additional, value-type-dependent properties.
   * <p>
   * For variables of type Object, the following properties are returned:
   * <p>
   * objectTypeName: A string representation of the object's type name, e.g. "com.example.MyObject".
   * serializationDataFormat: The serialization format used to store the variable, e.g. "application/xml".
   */
  private Map<String, Object> valueInfo;

  /**
   * The process definition key of the process model, where the variable was created.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String processDefinitionKey;

  /**
   * The process definition id of the process model, where the variable was used.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String processDefinitionId;

  /**
   * The process instance id of the process instance, where the variable was used.
   *
   * Note: This field is required in order to be imported to Optimize.
   */
  private String processInstanceId;

  /**
   * The version of the variable value. While a process instance is running
   * the same variable can be updated several times. This value indicates
   * which update number this variable is.
   * <p>
   * Note: This field is required in order to be imported to Optimize.
   */
  private Long version;

  /**
   * The field states the engine the variable is coming from.
   * In Optimize you can configure multiple engines to import data from.
   * Each engine configuration should have an unique engine alias associated
   * with it.
   * <p>
   * Note: This field is required in order to be imported to Optimize.
   */
  private String engineAlias;

  /**
   * The field states the tenant this variable instance belongs to.
   *
   * Note: Might be null if no tenant is assigned.
   */
  private String tenantId;

}
