package io.camunda.archunit;

import static io.camunda.archunit.rules.ConditionalOnPropertyRules.notUseCamelOrKebabInConditionalOnPropertyOnMethods;
import static io.camunda.archunit.rules.ConditionalOnPropertyRules.notUseCamelOrKebabInConditionalOnPropertyOnTypes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = {"io.camunda.application", "io.camunda.webapps"},
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ConditionalOnPropertyNamingArchTest {

  @ArchTest
  static final ArchRule NO_CAMEL_OR_KEBAB_IN_CONDITIONAL_ON_PROPERTY_ON_TYPES =
      notUseCamelOrKebabInConditionalOnPropertyOnTypes();

  @ArchTest
  static final ArchRule NO_CAMEL_OR_KEBAB_IN_CONDITIONAL_ON_PROPERTY_ON_METHODS =
      notUseCamelOrKebabInConditionalOnPropertyOnMethods();
}
