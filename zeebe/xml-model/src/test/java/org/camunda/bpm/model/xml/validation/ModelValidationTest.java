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

import static org.assertj.core.api.Assertions.*;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.validation.ModelValidationResultsImpl;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.instance.Bird;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Daniel Meyer
 */
public class ModelValidationTest {

  protected ModelInstance modelInstance;

  @Before
  public void parseModel() {
    TestModelParser modelParser = new TestModelParser();
    String testXml = "org/camunda/bpm/model/xml/testmodel/instance/UnknownAnimalTest.xml";
    InputStream testXmlAsStream = this.getClass().getClassLoader().getResourceAsStream(testXml);

    modelInstance = modelParser.parseModelFromStream(testXmlAsStream);
  }

  @Test
  public void shouldValidateWithEmptyList() {
    ValidationResults results = modelInstance.validate(List.of());

    assertThat(results).isNotNull();
    assertThat(results.hasErrors()).isFalse();
  }

  @Test
  public void shouldCollectWarnings() {
    List<ModelElementValidator<?>> validators = List.of(new IsAdultWarner());

    ValidationResults results = modelInstance.validate(validators);

    assertThat(results).isNotNull();
    assertThat(results.hasErrors()).isFalse();
    assertThat(results.getErrorCount()).isEqualTo(0);
    assertThat(results.getWarinigCount()).isEqualTo(7);
  }

  @Test
  public void shouldCollectErrors() {
    List<ModelElementValidator<?>> validators = List.of(new IllegalBirdValidator("tweety"));

    ValidationResults results = modelInstance.validate(validators);

    assertThat(results).isNotNull();
    assertThat(results.hasErrors()).isTrue();
    assertThat(results.getErrorCount()).isEqualTo(1);
    assertThat(results.getWarinigCount()).isEqualTo(0);
  }

  @Test
  public void shouldWriteResults() {
    List<ModelElementValidator<?>> validators = List.of(new IllegalBirdValidator("tweety"));

    ValidationResults results = modelInstance.validate(validators);

    StringWriter stringWriter = new StringWriter();
    results.write(stringWriter, new TestResultFormatter());

    assertThat(stringWriter.toString()).isEqualTo("tweety\n\tERROR (20): Bird tweety is illegal\n");
  }

  @Test
  public void shouldWriteResultsUntilMaxSize() {
    // Given
    int maxSize = 120;

    // adds 7 elements with warnings of size 30, and an element prefix of size 8
    // total size for 1 warning = 38
    List<ModelElementValidator<?>> validators = List.of(new IsAdultWarner());

    var results = modelInstance.validate(validators);
    var stringWriter = new StringWriter();

    // When
    results.write(stringWriter, new TestResultFormatter(), maxSize);

    // it has enough size to print 3 warnings, but it will only print 2,
    // because it needs to accommodate the suffix too in the max size.
    assertThat(stringWriter.toString())
        .describedAs("2 lines for 2 element names, 2 line for 2 warnings and one for the suffix")
        .hasLineCount(5)
        .describedAs(
            "shall contain only one error/warning and mention the count of the missing ones")
        .endsWith(String.format(TestResultFormatter.OMITTED_RESULTS_SUFFIX_FORMAT, 5));
  }

  @Test
  public void shouldCombineDifferentValidationResults() {
    // Given
    int maxSize = 120;

    // has 7 warnings
    var results1 = modelInstance.validate(List.of(new IsAdultWarner()));
    // has 1 error
    var results2 = modelInstance.validate(List.of(new IllegalBirdValidator("tweety")));
    var stringWriter = new StringWriter();

    // When
    var results = new ModelValidationResultsImpl(results1, results2);
    results.write(stringWriter, new TestResultFormatter(), maxSize);

    // it has enough size to print 3 warnings, but it will only print 2,
    // because it needs to accommodate the suffix too in the max size.
    assertThat(stringWriter.toString())
        .describedAs("2 lines for 2 element names, 2 line for 2 warnings and one for the suffix")
        .hasLineCount(5)
        .describedAs(
            "shall contain only one error/warning and mention the count of the missing ones")
        .endsWith(String.format(TestResultFormatter.OMITTED_RESULTS_SUFFIX_FORMAT, 6));
  }

  @Test
  public void shouldReturnResults() {
    List<ModelElementValidator<?>> validators =
        List.of(new IllegalBirdValidator("tweety"), new IsAdultWarner());

    ValidationResults results = modelInstance.validate(validators);

    assertThat(results.getErrorCount()).isEqualTo(1);
    assertThat(results.getWarinigCount()).isEqualTo(7);

    var resultsByElement = results.getResults();
    assertThat(resultsByElement.size()).isEqualTo(7);

    for (var resultEntry : resultsByElement.entrySet()) {
      Bird element = (Bird) resultEntry.getKey();
      List<ValidationResult> validationResults = resultEntry.getValue();
      assertThat(element).isNotNull();
      assertThat(validationResults).isNotNull();

      if (element.getId().equals("tweety")) {
        assertThat(validationResults.size()).isEqualTo(2);
        ValidationResult error = validationResults.remove(0);
        assertThat(error.getType()).isEqualTo(ValidationResultType.ERROR);
        assertThat(error.getCode()).isEqualTo(20);
        assertThat(error.getMessage()).isEqualTo("Bird tweety is illegal");
        assertThat(error.getElement()).isEqualTo(element);
      } else {
        assertThat(validationResults.size()).isEqualTo(1);
      }

      ValidationResult warning = validationResults.get(0);
      assertThat(warning.getType()).isEqualTo(ValidationResultType.WARNING);
      assertThat(warning.getCode()).isEqualTo(10);
      assertThat(warning.getMessage()).isEqualTo("Is not an adult");
      assertThat(warning.getElement()).isEqualTo(element);
    }
  }
}
