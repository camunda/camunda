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
package io.zeebe.engine.util;

import org.junit.Rule;
import org.junit.Test;

public class LogStreamPrinterTest {

  @Rule public StreamProcessorRule rule = new StreamProcessorRule();

  /**
   * This tests just assures that we do not remove this utility as unused code, as it's only purpose
   * is to be used temporarily for debugging
   */
  @Test
  public void testExistanceOfClass() {
    rule.printAllRecords();
  }
}
