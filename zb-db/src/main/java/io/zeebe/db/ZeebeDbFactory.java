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
