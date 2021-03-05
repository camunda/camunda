/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db;

import java.io.File;

/**
 * Represents the zeebe database factory. The {@link ColumnFamilyNames} has to be an enum and
 * specifies the different column families for the zeebe database.
 *
 * @param <ColumnFamilyNames> the names of the column families
 */
public interface ZeebeDbFactory<ColumnFamilyNames extends Enum<ColumnFamilyNames>> {

  /**
   * Creates a zeebe database in the given directory.
   *
   * @param pathName the path where the database should be created
   * @return the created zeebe database
   */
  ZeebeDb<ColumnFamilyNames> createDb(File pathName);
}
