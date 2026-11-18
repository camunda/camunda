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
package org.camunda.bpm.model.xml.impl.validation;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResultFormatter;
import org.camunda.bpm.model.xml.validation.ValidationResults;

/**
 * @author Daniel Meyer
 */
public class ModelValidationResultsImpl implements ValidationResults {

  protected Map<ModelElementInstance, List<ValidationResult>> collectedResults;

  protected int errorCount;
  protected int warningCount;

  public ModelValidationResultsImpl(
      Map<ModelElementInstance, List<ValidationResult>> collectedResults,
      int errorCount,
      int warningCount) {
    this.collectedResults = collectedResults;
    this.errorCount = errorCount;
    this.warningCount = warningCount;
  }

  public ModelValidationResultsImpl(ValidationResults... validationResults) {
    collectedResults = new HashMap<>();
    for (var entry : validationResults) {
      collectedResults.putAll(entry.getResults());
      errorCount += entry.getErrorCount();
      warningCount += entry.getWarinigCount();
    }
  }

  @Override
  public boolean hasErrors() {
    return errorCount > 0;
  }

  @Override
  public int getErrorCount() {
    return errorCount;
  }

  @Override
  public int getWarinigCount() {
    return warningCount;
  }

  @Override
  public void write(StringWriter writer, ValidationResultFormatter formatter) {
    for (Entry<ModelElementInstance, List<ValidationResult>> entry : collectedResults.entrySet()) {

      ModelElementInstance element = entry.getKey();
      List<ValidationResult> results = entry.getValue();

      formatter.formatElement(writer, element);

      for (ValidationResult result : results) {
        formatter.formatResult(writer, result);
      }
    }
  }

  @Override
  public void write(StringWriter writer, ValidationResultFormatter formatter, int maxSize) {
    int printedCount = 0;
    int previousLength = 0;
    for (var entry : collectedResults.entrySet()) {
      var element = entry.getKey();
      var results = entry.getValue();

      formatter.formatElement(writer, element);

      for (var result : results) {
        formatter.formatResult(writer, result);

        // Size and Length are not necessarily the same, depending on the encoding of the string.
        int currentSize = writer.getBuffer().toString().getBytes().length;
        int currentLength = writer.getBuffer().length();
        if (!canAccommodateResult(maxSize, currentSize, printedCount, formatter)) {
          writer.getBuffer().setLength(previousLength);
          int remaining = errorCount + warningCount - printedCount;
          formatter.formatSuffixWithOmittedResultsCount(writer, remaining);
          return;
        }
        printedCount++;
        previousLength = currentLength;
      }
    }
  }

  private boolean canAccommodateResult(
      int maxSize, int currentSize, int printedCount, ValidationResultFormatter formatter) {
    boolean isLastItemToPrint = printedCount == errorCount + warningCount - 1;
    if (isLastItemToPrint && currentSize <= maxSize) {
      return true;
    }
    int remaining = errorCount + warningCount - printedCount;
    int suffixLength = formatter.getFormattedSuffixWithOmittedResultsSize(remaining);
    return currentSize + suffixLength <= maxSize;
  }

  @Override
  public Map<ModelElementInstance, List<ValidationResult>> getResults() {
    return collectedResults;
  }
}
