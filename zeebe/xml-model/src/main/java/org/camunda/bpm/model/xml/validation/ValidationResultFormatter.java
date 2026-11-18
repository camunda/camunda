/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.xml.validation;

import java.io.StringWriter;

import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * SPI which can be implemented to print out a summary of a validation result. See {@link
 * ValidationResults#write(StringWriter, ValidationResultFormatter)}
 *
 * @author Daniel Meyer
 * @since 7.6
 */
public interface ValidationResultFormatter {

  /**
   * formats an element in the summary
   *
   * @param writer the writer
   * @param element the element to write
   */
  void formatElement(StringWriter writer, ModelElementInstance element);

  /**
   * formats a validation result
   *
   * @param writer the writer
   * @param result the result to format
   */
  void formatResult(StringWriter writer, ValidationResult result);

  /**
   * formats a suffix with the count of omitted errors/warnings, to be used when writing with a
   * maximum output size limit. See {@link ValidationResults#write(StringWriter,
   * ValidationResultFormatter, int)}
   *
   * @param writer the writer
   * @param count the count of results omitted from the writer output
   */
  default void formatSuffixWithOmittedResultsCount(StringWriter writer, int count) {
    // Do NOTHING by default
  }

  /**
   * returns the size of the formatted suffix (donating the count of omitted results) in bytes
   *
   * @param count the count of results to be omitted from the writer output
   * @return the size of the formatted suffix in bytes
   */
  default int getFormattedSuffixWithOmittedResultsSize(int count) {
    return 0;
  }
}
