/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

/**
 * A table that holds the rows of one or more column families, keyed by partition and key.
 *
 * @param name the name of the table, without the configured prefix
 * @param shared whether several column families share the table, which then keeps the column family
 *     between the partition and the key in its primary key
 */
record StateTable(String name, boolean shared) {

  String primaryKeyName() {
    return name + "_PK";
  }
}
