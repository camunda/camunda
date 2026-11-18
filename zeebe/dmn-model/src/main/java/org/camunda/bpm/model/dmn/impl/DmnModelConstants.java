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
package org.camunda.bpm.model.dmn.impl;

public final class DmnModelConstants {

  /** The DMN 1.1 namespace */
  public static final String DMN11_NS = "http://www.omg.org/spec/DMN/20151101/dmn.xsd";
  public static final String DMN12_NS = "http://www.omg.org/spec/DMN/20180521/MODEL/";
  public static final String DMN13_NS = "https://www.omg.org/spec/DMN/20191111/MODEL/";
  public static final String DMN14_NS = "https://www.omg.org/spec/DMN/20211108/MODEL/";
  public static final String DMN15_NS = "https://www.omg.org/spec/DMN/20230324/MODEL/";
  public static final String LATEST_DMN_NS = DMN13_NS;

  /**
   * The DMN 1.1 namespace URL release with Camunda 7.4.0
   */
  public static final String DMN11_ALTERNATIVE_NS = "http://www.omg.org/spec/DMN/20151101/dmn11.xsd";

  /**
   * The DMN 1.3 namespace URL release with Camunda 7.13.0
   */
  public static final String DMN13_ALTERNATIVE_NS = "https://www.omg.org/spec/DMN/20191111/DMN13.xsd";

  /** The location of the DMN 1.1 XML schema. */
  public static final String DMN_11_SCHEMA_LOCATION = "org/camunda/bpm/model/dmn/schema/DMN11.xsd";
  public static final String DMN_12_SCHEMA_LOCATION = "org/camunda/bpm/model/dmn/schema/DMN12.xsd";
  public static final String DMN_13_SCHEMA_LOCATION = "org/camunda/bpm/model/dmn/schema/DMN13.xsd";
  public static final String DMN_14_SCHEMA_LOCATION = "org/camunda/bpm/model/dmn/schema/DMN14.xsd";
  public static final String DMN_15_SCHEMA_LOCATION = "org/camunda/bpm/model/dmn/schema/DMN15.xsd";
  /**
   * The location of the DMN 1.1 XML schema released with Camunda 7.4.0
   */
  public static final String DMN_11_ALTERNATIVE_SCHEMA_LOCATION = "org/camunda/bpm/model/dmn/schema/DMN11_Alternative.xsd";

  /** The FEEL namespace */
  public static final String FEEL_NS = "http://www.omg.org/spec/FEEL/20140401";
  public static final String FEEL12_NS = "http://www.omg.org/spec/DMN/20180521/FEEL/";
  public static final String FEEL13_NS = "https://www.omg.org/spec/DMN/20191111/FEEL/";
  public static final String FEEL14_NS = "https://www.omg.org/spec/DMN/20211108/FEEL/";
  public static final String FEEL15_NS = "https://www.omg.org/spec/DMN/20230324/FEEL/";

  /** Camunda namespace */
  public static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/dmn";

  /** DMN element */

  public static final String DMN_ELEMENT = "DMNElement";
  public static final String DMN_ELEMENT_ALLOWED_ANSWERS = "allowedAnswers";
  public static final String DMN_ELEMENT_ALLOWED_VALUE = "allowedValue";
  public static final String DMN_ELEMENT_ARTIFACT = "artifact";
  public static final String DMN_ELEMENT_ASSOCIATION = "association";
  public static final String DMN_ELEMENT_AUTHORITY_REQUIREMENT = "authorityRequirement";
  public static final String DMN_ELEMENT_BINDING = "binding";
  public static final String DMN_ELEMENT_BUSINESS_CONTEXT_ELEMENT = "businessContextElement";
  public static final String DMN_ELEMENT_BUSINESS_KNOWLEDGE_MODEL = "businessKnowledgeModel";
  public static final String DMN_ELEMENT_COLUMN = "column";
  public static final String DMN_ELEMENT_CONTEXT = "context";
  public static final String DMN_ELEMENT_CONTEXT_ENTRY = "contextEntry";
  public static final String DMN_ELEMENT_DECISION = "decision";
  public static final String DMN_ELEMENT_DECISION_MADE = "decisionMade";
  public static final String DMN_ELEMENT_DECISION_MAKER = "decisionMaker";
  public static final String DMN_ELEMENT_DECISION_OWNED = "decisionOwned";
  public static final String DMN_ELEMENT_DECISION_OWNER = "decisionOwner";
  public static final String DMN_ELEMENT_DECISION_RULE = "decisionRule";
  public static final String DMN_ELEMENT_DECISION_SERVICE = "decisionService";
  public static final String DMN_ELEMENT_DECISION_TABLE = "decisionTable";
  public static final String DMN_ELEMENT_DEFAULT_OUTPUT_ENTRY = "defaultOutputEntry";
  public static final String DMN_ELEMENT_DEFINITIONS = "definitions";
  public static final String DMN_ELEMENT_DESCRIPTION = "description";
  public static final String DMN_ELEMENT_DRG_ELEMENT = "drgElement";
  public static final String DMN_ELEMENT_DRG_ELEMENT_REFERENCE = "drgElement";
  public static final String DMN_ELEMENT_ELEMENT_COLLECTION = "elementCollection";
  public static final String DMN_ELEMENT_ENCAPSULATED_DECISION_REFERENCE = "encapsulatedDecision";
  public static final String DMN_ELEMENT_ENCAPSULATED_LOGIC = "encapsulatedLogic";
  public static final String DMN_ELEMENT_EXPRESSION = "expression";
  public static final String DMN_ELEMENT_EXTENSION_ELEMENTS = "extensionElements";
  public static final String DMN_ELEMENT_FORMAL_PARAMETER = "formalParameter";
  public static final String DMN_ELEMENT_FUNCTION_DEFINITION = "functionDefinition";
  public static final String DMN_ELEMENT_IMPACTED_PERFORMANCE_INDICATOR = "impactedPerformanceIndicator";
  public static final String DMN_ELEMENT_IMPACTING_DECISION = "impactingDecision";
  public static final String DMN_ELEMENT_IMPORT = "import";
  public static final String DMN_ELEMENT_IMPORTED_ELEMENT = "importedElement";
  public static final String DMN_ELEMENT_IMPORTED_VALUES = "importedValues";
  public static final String DMN_ELEMENT_INFORMATION_ITEM = "informationItem";
  public static final String DMN_ELEMENT_INFORMATION_REQUIREMENT = "informationRequirement";
  public static final String DMN_ELEMENT_INPUT = "input";
  public static final String DMN_ELEMENT_INPUT_CLAUSE = "inputClause";
  public static final String DMN_ELEMENT_INPUT_DATA = "inputData";
  public static final String DMN_ELEMENT_INPUT_DATA_REFERENCE = "inputData";
  public static final String DMN_ELEMENT_INPUT_DECISION_REFERENCE = "inputDecision";
  public static final String DMN_ELEMENT_INPUT_ENTRY = "inputEntry";
  public static final String DMN_ELEMENT_INPUT_EXPRESSION = "inputExpression";
  public static final String DMN_ELEMENT_INPUT_VALUES = "inputValues";
  public static final String DMN_ELEMENT_INVOCATION = "invocation";
  public static final String DMN_ELEMENT_ITEM_COMPONENT = "itemComponent";
  public static final String DMN_ELEMENT_ITEM_DEFINITION = "itemDefinition";
  public static final String DMN_ELEMENT_ITEM_DEFINITION_REFERENCE = "itemDefinition";
  public static final String DMN_ELEMENT_KNOWLEDGE_REQUIREMENT = "knowledgeRequirement";
  public static final String DMN_ELEMENT_KNOWLEDGE_SOURCE = "knowledgeSource";
  public static final String DMN_ELEMENT_LIST = "list";
  public static final String DMN_ELEMENT_LITERAL_EXPRESSION = "literalExpression";
  public static final String DMN_ELEMENT_NAMED_ELEMENT = "namedElement";
  public static final String DMN_ELEMENT_ORGANIZATION_UNIT = "organizationUnit";
  public static final String DMN_ELEMENT_OUTPUT = "output";
  public static final String DMN_ELEMENT_OUTPUT_CLAUSE = "outputClause";
  public static final String DMN_ELEMENT_OUTPUT_DECISION_REFERENCE = "outputDecision";
  public static final String DMN_ELEMENT_OUTPUT_ENTRY = "outputEntry";
  public static final String DMN_ELEMENT_OUTPUT_VALUES = "outputValues";
  public static final String DMN_ELEMENT_OWNER = "owner";
  public static final String DMN_ELEMENT_PARAMETER = "parameter";
  public static final String DMN_ELEMENT_PERFORMANCE_INDICATOR = "performanceIndicator";
  public static final String DMN_ELEMENT_QUESTION = "question";
  public static final String DMN_ELEMENT_REFERENCE = "DMNElementReference";
  public static final String DMN_ELEMENT_RELATION = "relation";
  public static final String DMN_ELEMENT_REQUIRED_AUTHORITY = "requiredAuthority";
  public static final String DMN_ELEMENT_REQUIRED_DECISION = "requiredDecision";
  public static final String DMN_ELEMENT_REQUIRED_INPUT = "requiredInput";
  public static final String DMN_ELEMENT_REQUIRED_KNOWLEDGE = "requiredKnowledge";
  public static final String DMN_ELEMENT_ROW = "row";
  public static final String DMN_ELEMENT_RULE = "rule";
  public static final String DMN_ELEMENT_SOURCE_REF = "sourceRef";
  public static final String DMN_ELEMENT_SUPPORTED_OBJECT = "supportedObjective";
  public static final String DMN_ELEMENT_TARGET_REF = "targetRef";
  public static final String DMN_ELEMENT_TEXT = "text";
  public static final String DMN_ELEMENT_TEXT_ANNOTATION = "textAnnotation";
  public static final String DMN_ELEMENT_TYPE = "type";
  public static final String DMN_ELEMENT_TYPE_REF = "typeRef";
  public static final String DMN_ELEMENT_UNARY_TESTS = "unaryTests";
  public static final String DMN_ELEMENT_USING_PROCESS = "usingProcess";
  public static final String DMN_ELEMENT_USING_TASK = "usingTask";
  public static final String DMN_ELEMENT_VARIABLE = "variable";

  /** DMN attributes */

  public static final String DMN_ATTRIBUTE_AGGREGATION = "aggregation";
  public static final String DMN_ATTRIBUTE_ASSOCIATION_DIRECTION = "associationDirection";
  public static final String DMN_ATTRIBUTE_EXPRESSION_LANGUAGE = "expressionLanguage";
  public static final String DMN_ATTRIBUTE_EXPORTER = "exporter";
  public static final String DMN_ATTRIBUTE_EXPORTER_VERSION = "exporterVersion";
  public static final String DMN_ATTRIBUTE_HIT_POLICY = "hitPolicy";
  public static final String DMN_ATTRIBUTE_HREF = "href";
  public static final String DMN_ATTRIBUTE_ID = "id";
  public static final String DMN_ATTRIBUTE_IMPORT_TYPE = "importType";
  public static final String DMN_ATTRIBUTE_IS_COLLECTION = "isCollection";
  public static final String DMN_ATTRIBUTE_LABEL = "label";
  public static final String DMN_ATTRIBUTE_LOCATION_URI = "locationURI";
  public static final String DMN_ATTRIBUTE_NAME = "name";
  public static final String DMN_ATTRIBUTE_NAMESPACE = "namespace";
  public static final String DMN_ATTRIBUTE_OUTPUT_LABEL = "outputLabel";
  public static final String DMN_ATTRIBUTE_PREFERRED_ORIENTATION = "preferredOrientation";
  public static final String DMN_ATTRIBUTE_TEXT_FORMAT = "textFormat";
  public static final String DMN_ATTRIBUTE_TYPE_LANGUAGE = "typeLanguage";
  public static final String DMN_ATTRIBUTE_TYPE_REF = "typeRef";
  public static final String DMN_ATTRIBUTE_URI = "URI";

  /** camunda extensions */

  public static final String CAMUNDA_ATTRIBUTE_INPUT_VARIABLE = "inputVariable";
  public static final String CAMUNDA_ATTRIBUTE_HISTORY_TIME_TO_LIVE = "historyTimeToLive";
  public static final String CAMUNDA_ATTRIBUTE_VERSION_TAG = "versionTag";

}
