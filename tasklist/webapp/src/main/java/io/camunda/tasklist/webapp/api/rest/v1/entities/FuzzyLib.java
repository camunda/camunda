package io.camunda.tasklist.webapp.api.rest.v1.entities;

import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.rule.Variable;
import org.antlr.runtime.RecognitionException;

public class FuzzyLib {

  public static void main(final String[] args) throws RecognitionException {
    // Define the FCL rules as a string
    final String fclRules =
        "FUNCTION_BLOCK TaskPriority\n" +
            "\n" +
            "VAR_INPUT\n" +
            "    Due : REAL;\n" +
            "    Priority : REAL;\n" +
            "END_VAR\n" +
            "\n" +
            "VAR_OUTPUT\n" +
            "    Output : REAL;\n" +
            "END_VAR\n" +
            "\n" +
            "FUZZIFY Due\n" +
            "    TERM Short_Time := (0,1) (2,1) (5,0);\n" +
            "    TERM Medium := (2,0) (5,1) (8,0);\n" +
            "    TERM High := (5,0) (8,1) (10,1);\n" +
            "END_FUZZIFY\n" +
            "\n" +
            "FUZZIFY Priority\n" +
            "    TERM Low := (0,1) (20,1) (50,0);\n" +
            "    TERM Medium := (20,0) (50,1) (80,0);\n" +
            "    TERM High := (50,0) (80,1) (100,1);\n" +
            "END_FUZZIFY\n" +
            "\n" +
            "DEFUZZIFY Output\n" +
            "    TERM Very_Low := (0,1) (2,1) (4,0);\n" +
            "    TERM Low := (2,0) (4,1) (6,0);\n" +
            "    TERM Medium := (4,0) (6,1) (8,0);\n" +
            "    TERM High := (6,0) (8,1) (10,0);\n" +
            "    TERM Urgent := (8,0) (10,1) (10,1);\n" +
            "    METHOD : COG;\n" +
            "END_DEFUZZIFY\n" +
            "\n" +
            "RULEBLOCK Rules\n" +
            "    AND : MIN;\n" +
            "    ACT : MIN;\n" +
            "    ACCU : MAX;\n" +
            "\n" +
            "    RULE 1 : IF Due IS Short_Time AND Priority IS High THEN Output IS Urgent;\n" +
            "    RULE 2 : IF Due IS Short_Time AND Priority IS Medium THEN Output IS High;\n" +
            "    RULE 3 : IF Due IS Medium AND Priority IS High THEN Output IS Medium;\n" +
            "    RULE 4 : IF Due IS Medium AND Priority IS Medium THEN Output IS Low;\n" +
            "    RULE 5 : IF Due IS High AND Priority IS Low THEN Output IS Very_Low;\n" +
            "    RULE 6 : IF Due IS High AND Priority IS Medium THEN Output IS Low;\n" +
            "END_RULEBLOCK\n" +
            "\n" +
            "END_FUNCTION_BLOCK\n";  // This was missing in the previous version

    // Create FIS from string
    final FIS fis = FIS.createFromString(fclRules, true);


    // Check if successfully created
    if (fis == null) {
      System.err.println("Error: Could not create FIS from the provided string.");
      return;
    }

    // Set input values
    fis.setVariable("Due", 9);       // Example: DueDate = 3
    fis.setVariable("Priority", 10); // Example: Priority = 80

    // Evaluate the fuzzy system

    fis.evaluate();

    // Get the crisp output
    final Variable output = fis.getVariable("Output");


    // Print results
    System.out.println("Output Classification: " + output.getValue());


  }
}
