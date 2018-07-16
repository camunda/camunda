package org.camunda.optimize.upgrade.main;

import org.apache.commons.text.StrSubstitutor;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom21To22;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class UpgradeMain {

  private static Map<String, Upgrade> upgrades = new HashMap<>();

  static {
    upgrades.put("2.2.0", new UpgradeFrom21To22());
  }

  public static void main(String... args) {

    String targetVersion = removeAppendixFromVersion(Version.VERSION);
    Upgrade upgrade = upgrades.get(targetVersion);

    if (upgrade == null) {
      String errorMessage =
        "It was not possible to upgrade Optimize to version " + targetVersion + ".\n" +
        "Either this is the wrong upgrade jar or the jar is flawed. \n" +
        "Please contact the Optimize support for help!";
      throw new UpgradeRuntimeException(errorMessage);
    }

    if (args.length == 0 || !args[0].contains("skip-warning")) {
      printWarning(upgrade.getInitialVersion(), upgrade.getTargetVersion());
    }

    System.out.println("Execute upgrade...");
    upgrade.performUpgrade();
    System.out.println("Finished upgrade successfully!");
  }

  private static String removeAppendixFromVersion(String versionWithAppendix) {
    // The version might have an appendix like 2.2.0-SNAPSHOT
    int indexOfMinus = versionWithAppendix.indexOf("-");
    indexOfMinus = indexOfMinus == -1 ? versionWithAppendix.length() : indexOfMinus;
    return versionWithAppendix.substring(0, indexOfMinus);
  }

  private static void printWarning(String fromVersion, String toVersion) {
    Map<String, String> valuesMap = new HashMap<>();
    valuesMap.put("warning", "================================ WARNING! ================================");
    valuesMap.put("fromVersion", fromVersion);
    valuesMap.put("toVersion", toVersion);
    StrSubstitutor sub = new StrSubstitutor(valuesMap);
    String message =
      "\n\n" +
        "${warning}\n\n" +
        "Please be aware that you are about to upgrade the Optimize data \n" +
        "schema in Elasticsearch from version ${fromVersion} to ${toVersion}. \n" +
        "There is no warranty that this upgrade might not break the data \n" +
        "structure in Elasticsearch. Therefore, it is highly recommended to \n" +
        "create a backup of your data in Elasticsearch in case something goes wrong. \n" +
        "\n" +
        "Do you want to proceed? [yes/no] \n" +
        "\n" +
        "1. yes = I already did a backup and want to proceed. \n" +
        "2. no = Thanks for reminding me, I want to do a backup first. \n" +
        "\n" +
        "Your answer (type your answer and hit enter): " ;

    message = sub.replace(message);
    System.out.print(message);

    String answer = "";
    while(!answer.equals("yes")) {
      Scanner console = new Scanner(System.in);
      answer = console.next().trim().toLowerCase();
      System.out.println();
      if (answer.equals("no")) {
        System.out.println("The Optimize upgrade was aborted.");
        System.exit(1);
      } else if (!answer.equals("yes")) {
        String text = "Your answer was '" + answer + "'. The only accepted answers are 'yes' or 'no'. \n" +
          "\n" +
          "Your answer (type your answer and hit enter): ";
        System.out.print(text);
      }
    }
  }
}
