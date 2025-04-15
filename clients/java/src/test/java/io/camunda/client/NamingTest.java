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
package io.camunda.client;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.camunda.client",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class NamingTest {

  @ArchTest
  public static final ArchRule RULE_CLIENT_API_METHODS_SHOULD_NOT_CONTAIN_QUERY =
      noMethods()
          .that()
          .areDeclaredIn(CamundaClient.class)
          .should()
          .haveNameContaining("Query")
          .because("Client API methods should use \"SearchRequest\" instead.");

  @ArchTest
  public static final ArchRule RULE_CLIENT_API_CLASSES_SHOULD_NOT_CONTAIN_QUERY =
      noClasses()
          .that()
          .resideOutsideOfPackage("io.camunda.client.protocol.rest..")
          .should()
          .haveSimpleNameContaining("Query")
          .because("Client classes should use \"SearchRequest\" instead.");

  @ArchTest
  public static final ArchRule RULE_CLIENT_API_METHODS_SHOULD_NOT_CONTAIN_FLOW_NODE =
      noMethods()
          .that()
          .areDeclaredIn(CamundaClient.class)
          .should()
          .haveNameContaining("flowNode")
          .orShould()
          .haveNameContaining("FlowNode")
          .orShould()
          .haveNameContaining("Flownode")
          .orShould()
          .haveNameContaining("flownode")
          .because("Client API methods should use \"Element\" instead.");

  @ArchTest
  public static final ArchRule RULE_CLIENT_API_CLASSES_SHOULD_NOT_CONTAIN_FLOW_NODE =
      noClasses()
          .that()
          .resideOutsideOfPackage("io.camunda.client.protocol.rest..")
          .should()
          .haveSimpleNameContaining("flowNode")
          .orShould()
          .haveSimpleNameContaining("FlowNode")
          .orShould()
          .haveSimpleNameContaining("Flownode")
          .orShould()
          .haveSimpleNameContaining("flownode")
          .because("Client classes should use \"Element\" instead.");
}
