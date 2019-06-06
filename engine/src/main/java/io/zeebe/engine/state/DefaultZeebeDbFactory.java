/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.state;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;

public final class DefaultZeebeDbFactory {

  /**
   * The default zeebe database factory, which is used in most of the places except for the
   * exporters.
   */
  public static final ZeebeDbFactory<ZbColumnFamilies> DEFAULT_DB_FACTORY =
      defaultFactory(ZbColumnFamilies.class);

  /**
   * Returns the default zeebe database factory which is used in the broker.
   *
   * @param columnFamilyNamesClass the enum class, which contains the column family names
   * @param <ColumnFamilyNames> the type of the enum
   * @return the created zeebe database factory
   */
  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeDbFactory<ColumnFamilyNames> defaultFactory(
          Class<ColumnFamilyNames> columnFamilyNamesClass) {
    // one place to replace the zeebe database implementation
    return ZeebeRocksDbFactory.newFactory(columnFamilyNamesClass);
  }
}
