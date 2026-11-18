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
import java.util.Formatter;

import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.testmodel.instance.FlyingAnimal;

/**
 * @author Daniel Meyer
 */
@SuppressWarnings("resource")
public class TestResultFormatter implements ValidationResultFormatter {

  public static final String OMITTED_RESULTS_SUFFIX_FORMAT = "and %d more errors and/or warnings";

  @Override
  public void formatElement(StringWriter writer, ModelElementInstance element) {
    Formatter formatter = new Formatter(writer);

    if (element instanceof FlyingAnimal) {
      formatter.format("%s\n", ((FlyingAnimal) element).getId());
    } else {
      formatter.format("%s\n", element.getElementType().getTypeName());
    }

    formatter.flush();
  }

  @Override
  public void formatResult(StringWriter writer, ValidationResult result) {
    new Formatter(writer)
        .format("\t%s (%d): %s\n", result.getType(), result.getCode(), result.getMessage())
        .flush();
  }

  @Override
  public void formatSuffixWithOmittedResultsCount(StringWriter writer, int count) {
    new Formatter(writer).format(OMITTED_RESULTS_SUFFIX_FORMAT, count).flush();
  }

  @Override
  public int getFormattedSuffixWithOmittedResultsSize(int count) {
    return String.format(OMITTED_RESULTS_SUFFIX_FORMAT, count).getBytes().length;
  }
}
