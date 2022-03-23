/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.HitPolicy;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DmnModels {
  //invoice variables
  public static final String OUTPUT_CLASSIFICATION_ID = "classificationOutput";
  public static final String OUTPUT_AUDIT_ID = "auditOutput";
  public static final String STRING_OUTPUT_ID = "stringOutput";
  public static final String INTEGER_OUTPUT_ID = "integerOutput";
  public static final String INPUT_AMOUNT_ID = "amountInput";
  public static final String INPUT_CATEGORY_ID = "categoryInput";
  public static final String STRING_INPUT_ID = "stringInput";
  public static final String INTEGER_INPUT_ID = "integerInput";
  public static final String INPUT_INVOICE_DATE_ID = "invoiceDateInput";
  public static final String INPUT_VARIABLE_INVOICE_CATEGORY = "invoiceCategory";
  public static final String INPUT_VARIABLE_AMOUNT = "amount";
  public static final String INPUT_VARIABLE_STRING_INPUT = "stringInput";
  public static final String INPUT_VARIABLE_INTEGER_INPUT = "integerInput";
  public static final String INPUT_VARIABLE_INVOICE_DATE = "invoiceDate";
  public static final String OUTPUT_VARIABLE_AUDIT = "audit";
  public static final String OUTPUT_VARIABLE_CLASSIFICATION = "invoiceClassification";
  public static final String OUTPUT_VARIABLE_STRING_OUTPUT = "stringOutput";
  public static final String OUTPUT_VARIABLE_INTEGER_OUTPUT = "integerOutput";

  // dish variables
  public static final String INPUT_SEASON_ID = "seasonInput";
  public static final String INPUT_NUMBER_OF_GUESTS_ID = "numberOfGuestsInput";
  public static final String INPUT_GUEST_WITH_CHILDREN_ID = "guestsWithChildrenInput";
  public static final String INPUT_VARIABLE_SEASON = "season";
  public static final String INPUT_VARIABLE_NUMBER_OF_GUESTS = "guestCount";
  public static final String INPUT_VARIABLE_GUEST_WITH_CHILDREN = "guestsWithChildren";
  public static final String OUTPUT_BEVERAGES = "OutputClause_99999";

  //rules
  public static final String INVOICE_RULE_1_ID = "invoiceRule1";
  public static final String INVOICE_RULE_2_ID = "invoiceRule2";
  public static final String INVOICE_RULE_3_ID = "invoiceRule3";
  public static final String INVOICE_RULE_4_ID = "invoiceRule4";
  public static final String BEVERAGES_RULE_1_ID = "row-506282952-9";
  public static final String BEVERAGES_RULE_2_ID = "row-506282952-12";
  public static final String DISH_RULE_1 = "dishRule1";
  public static final String DISH_RULE_2 = "dishRule2";
  public static final String DISH_RULE_3 = "dishRule3";
  public static final String DISH_RULE_4 = "dishRule4";
  public static final String DISH_RULE_5 = "dishRule5";
  public static final String DISH_RULE_6 = "dishRule6";
  public static final String DISH_RULE_7 = "dishRule7";

  // @formatter:off
  public static DmnModelInstance createDefaultDmnModel() {
    return createDefaultDmnModel("invoiceClassification");
  }

  public static DmnModelInstance createDefaultDmnModelNoInputAndOutputLabels() {
    return createDefaultDmnModelNoInputAndOutputLabels("invoiceClassification");
  }

  public static DmnModelInstance createDefaultDmnModelNoInputAndOutputLabels(final String key) {
    return DmnModelGenerator
      .create()
        .decision()
          .decisionDefinitionVersionTag("aVersionTag")
          .decisionDefinitionKey(key)
          .decisionDefinitionName("Invoice Classification")
          .addInput(null, INPUT_AMOUNT_ID, INPUT_VARIABLE_AMOUNT, DecisionTypeRef.DOUBLE)
          .addInput(null, INPUT_CATEGORY_ID, INPUT_VARIABLE_INVOICE_CATEGORY, DecisionTypeRef.STRING)
          .addOutput(null, OUTPUT_CLASSIFICATION_ID,OUTPUT_VARIABLE_CLASSIFICATION, DecisionTypeRef.STRING)
          .addOutput(null, OUTPUT_AUDIT_ID, OUTPUT_VARIABLE_AUDIT,  DecisionTypeRef.BOOLEAN)
          .rule()
            .addStringInputEntry("< 250")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"day-to-day expense\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_1_ID)
          .rule()
            .addStringInputEntry("[250..1000]")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_2_ID)
          .rule()
            .addStringInputEntry("> 1000")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"exceptional\"")
            .addStringOutputEntry("true")
          .buildRule(INVOICE_RULE_3_ID)
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("\"Travel Expenses\"")
            .addStringOutputEntry("\"day-to-day expense\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_4_ID)
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("\"Software License Costs\"")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule("row-49839158-2")
        .buildDecision()
      .build();
  }

  public static DmnModelInstance createDefaultDmnModel(final String key) {
    return DmnModelGenerator
      .create()
        .decision()
          .decisionDefinitionVersionTag("aVersionTag")
          .decisionDefinitionKey(key)
          .decisionDefinitionName("Invoice Classification")
          .addInput("Invoice Amount", INPUT_AMOUNT_ID, INPUT_VARIABLE_AMOUNT, DecisionTypeRef.DOUBLE)
          .addInput("Invoice Category", INPUT_CATEGORY_ID, INPUT_VARIABLE_INVOICE_CATEGORY, DecisionTypeRef.STRING)
          .addOutput("Classification", OUTPUT_CLASSIFICATION_ID,OUTPUT_VARIABLE_CLASSIFICATION, DecisionTypeRef.STRING)
          .addOutput("Audit", OUTPUT_AUDIT_ID, OUTPUT_VARIABLE_AUDIT,  DecisionTypeRef.BOOLEAN)
          .rule()
            .addStringInputEntry("< 250")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"day-to-day expense\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_1_ID)
          .rule()
            .addStringInputEntry("[250..1000]")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_2_ID)
          .rule()
            .addStringInputEntry("> 1000")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"exceptional\"")
            .addStringOutputEntry("true")
          .buildRule(INVOICE_RULE_3_ID)
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("\"Travel Expenses\"")
            .addStringOutputEntry("\"day-to-day expense\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_4_ID)
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("\"Software License Costs\"")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule("row-49839158-2")
        .buildDecision()
      .build();
  }

  public static DmnModelInstance createInputEqualsOutput() {
    return DmnModelGenerator
      .create()
        .decision()
          .decisionDefinitionVersionTag("aVersionTag")
          .decisionDefinitionKey("inputEqualsOutput")
          .decisionDefinitionName("Input equals output")
          .addInput("String Input", STRING_INPUT_ID, INPUT_VARIABLE_STRING_INPUT, DecisionTypeRef.STRING)
          .addInput("Integer Input", INTEGER_INPUT_ID, INPUT_VARIABLE_INTEGER_INPUT, DecisionTypeRef.STRING)
          .addOutput("String Output", STRING_OUTPUT_ID, OUTPUT_VARIABLE_STRING_OUTPUT,  DecisionTypeRef.STRING)
          .addOutput("Integer Output", INTEGER_OUTPUT_ID, OUTPUT_VARIABLE_INTEGER_OUTPUT,  DecisionTypeRef.STRING)
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("")
            .addStringOutputEntry(INPUT_VARIABLE_STRING_INPUT)
            .addStringOutputEntry(INPUT_VARIABLE_INTEGER_INPUT)
          .buildRule()
        .buildDecision()
      .build();
  }

  public static DmnModelInstance createDecisionDefinitionWoName() {
    return DmnModelGenerator
      .create()
        .decision()
          .decisionDefinitionKey("invoiceClassification")
          .decisionDefinitionName(null)
          .addOutput("Classification", OUTPUT_CLASSIFICATION_ID,OUTPUT_VARIABLE_CLASSIFICATION, DecisionTypeRef.STRING)
          .addOutput("Audit", OUTPUT_AUDIT_ID, OUTPUT_VARIABLE_AUDIT,  DecisionTypeRef.BOOLEAN)
          .addInput("Invoice Amount", INPUT_AMOUNT_ID, INPUT_VARIABLE_AMOUNT, DecisionTypeRef.DOUBLE)
          .addInput("Invoice Category", INPUT_CATEGORY_ID, INPUT_VARIABLE_INVOICE_CATEGORY, DecisionTypeRef.STRING)
          .rule()
            .addStringInputEntry("< 250")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"day-to-day expense\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_1_ID)
          .rule()
            .addStringInputEntry("[250..1000]")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_2_ID)
          .rule()
            .addStringInputEntry("> 1000")
            .addStringInputEntry("\"Misc\"")
            .addStringOutputEntry("\"exceptional\"")
            .addStringOutputEntry("true")
          .buildRule(INVOICE_RULE_3_ID)
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("\"Travel Expenses\"")
            .addStringOutputEntry("\"day-to-day expense\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_4_ID)
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("\"Software License Costs\"")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule("row-49839158-2")
        .buildDecision()
      .build();
    }

  public static DmnModelInstance createDecisionDefinitionWithDate() {
    return DmnModelGenerator
      .create()
        .decision()
          .decisionDefinitionKey("invoiceClassification")
          .decisionDefinitionName(null)
          .addOutput("Classification", OUTPUT_CLASSIFICATION_ID, OUTPUT_VARIABLE_CLASSIFICATION, DecisionTypeRef.STRING)
          .addOutput("Audit", OUTPUT_AUDIT_ID, OUTPUT_VARIABLE_AUDIT,  DecisionTypeRef.BOOLEAN)
          .addInput("Invoice Amount", INPUT_AMOUNT_ID, INPUT_VARIABLE_AMOUNT, DecisionTypeRef.DOUBLE)
          .addInput("Invoice Category", INPUT_CATEGORY_ID, INPUT_VARIABLE_INVOICE_CATEGORY, DecisionTypeRef.STRING)
          .addInput("Invoice Date", INPUT_INVOICE_DATE_ID, INPUT_VARIABLE_INVOICE_DATE, DecisionTypeRef.DATE)
          .rule()
            .addStringInputEntry("< 250")
            .addStringInputEntry("\"Misc\"")
            .addStringInputEntry("-")
            .addStringOutputEntry("\"day-to-day expense\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_1_ID)
          .rule()
            .addStringInputEntry("[250..1000]")
            .addStringInputEntry("\"Misc\"")
            .addStringInputEntry("< date and time(\"2019-01-01T00:00:00\")")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_2_ID)
          .rule()
            .addStringInputEntry("> 1000")
            .addStringInputEntry("\"Misc\"")
            .addStringInputEntry("< date and time(\"2019-01-01T00:00:00\")")
            .addStringOutputEntry("\"exceptional\"")
            .addStringOutputEntry("true")
          .buildRule(INVOICE_RULE_3_ID)
          .rule()
            .addStringInputEntry("[250..2000]")
            .addStringInputEntry("\"Misc\"")
            .addStringInputEntry(">= date and time(\"2019-01-01T00:00:00\")")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule("DecisionRule_1ws8s8u")
          .rule()
            .addStringInputEntry("> 2000")
            .addStringInputEntry("\"Misc\"")
            .addStringInputEntry("< date and time(\"2019-01-01T00:00:00\")")
            .addStringOutputEntry("\"exceptional\"")
            .addStringOutputEntry("true")
          .buildRule("DecisionRule_0h99uwx")
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("\"Travel Expenses\"")
            .addStringInputEntry("")
            .addStringOutputEntry("\"day-to-day expense\"")
            .addStringOutputEntry("false")
          .buildRule(INVOICE_RULE_4_ID)
          .rule()
            .addStringInputEntry("")
            .addStringInputEntry("\"Software License Costs\"")
            .addStringInputEntry("")
            .addStringOutputEntry("\"budget\"")
            .addStringOutputEntry("false")
          .buildRule("row-49839158-2")
        .buildDecision()
      .build();
    }

  public static DmnModelInstance createDecideDishDecisionDefinition() {
  return DmnModelGenerator
      .create()
        .decision()
          .setHitPolicy(HitPolicy.COLLECT)
          .decisionDefinitionName("Beverages")
          .decisionDefinitionKey("beverages")
          .addInput("Guests with children", "InputClause_0bo3uen","guestsWithChildren", DecisionTypeRef.BOOLEAN)
          .addOutput("Beverages", OUTPUT_BEVERAGES,"beverages", DecisionTypeRef.STRING)
          .rule()
            .addStringInputEntry("true")
            .addStringOutputEntry("\"Aecht Schlenkerla Rauchbier\"")
          .buildRule(BEVERAGES_RULE_1_ID)
          .rule()
            .addStringInputEntry("false")
            .addStringOutputEntry("\"Water\"")
          .buildRule(BEVERAGES_RULE_2_ID)
        .buildDecision()
        .decision()
          .decisionDefinitionKey("dish")
          .decisionDefinitionName("Dish")
          .addInput("Season", INPUT_SEASON_ID, INPUT_VARIABLE_SEASON, DecisionTypeRef.STRING)
          .addInput("How many guestst", INPUT_NUMBER_OF_GUESTS_ID, INPUT_VARIABLE_NUMBER_OF_GUESTS, DecisionTypeRef.INTEGER)
          .addOutput("Dish", "OutputClause_0lfar1z", "desiredDish", DecisionTypeRef.STRING)
          .rule()
            .addStringInputEntry("not(\"Fall\", \"Winter\", \"Spring\", \"Summer\")")
            .addStringInputEntry(">= 0")
            .addStringOutputEntry("\"Instant Soup\"")
          .buildRule(DISH_RULE_1)
          .rule()
            .addStringInputEntry("\"Fall\"")
            .addStringInputEntry("<= 8")
            .addStringOutputEntry("\"Spareribs\"")
          .buildRule(DISH_RULE_2)
          .rule()
            .addStringInputEntry("\"Winter\"")
            .addStringInputEntry("<= 8")
            .addStringOutputEntry("\"Roastbeef\"")
          .buildRule(DISH_RULE_3)
          .rule()
            .addStringInputEntry("\"Spring\"")
            .addStringInputEntry("<= 4")
            .addStringOutputEntry("\"Dry Aged Gourmet Steak\"")
          .buildRule(DISH_RULE_4)
          .rule()
            .addStringInputEntry("\"Spring\"")
            .addStringInputEntry("[5..8]")
            .addStringOutputEntry("\"Steak\"")
          .buildRule(DISH_RULE_5)
          .rule()
            .addStringInputEntry("\"Fall\",\"Winter\",\"Spring\"")
            .addStringInputEntry("> 8")
            .addStringOutputEntry("\"Stew\"")
          .buildRule(DISH_RULE_6)
          .rule()
            .addStringInputEntry("\"Summer\"")
            .addStringInputEntry("")
            .addStringOutputEntry("\"Light Salad and a nice Steak\"")
          .buildRule(DISH_RULE_7)
        .buildDecision()
      .build();
  }

  // @formatter:on
}
