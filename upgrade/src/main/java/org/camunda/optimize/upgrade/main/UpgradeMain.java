package org.camunda.optimize.upgrade.main;

import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom21To22;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom22To23;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom23To24;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom24To25;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class UpgradeMain {

  private static final Set<String> ANSWER_OPTIONS_YES = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("y", "yes"))
  );

  private static final Set<String> ANSWER_OPTIONS_NO = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("n", "no"))
  );

  private static Map<String, Upgrade> upgrades = new HashMap<>();

  static {
    upgrades.put("2.2.0", new UpgradeFrom21To22());
    upgrades.put("2.3.0", new UpgradeFrom22To23());
    upgrades.put("2.4.0", new UpgradeFrom23To24());
    upgrades.put("2.5.0", new UpgradeFrom24To25());
  }

  public static void main(String... args) {

    String targetVersion = removeAppendixFromVersion();
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
    System.exit(0);
  }

  private static String removeAppendixFromVersion() {
    String engineVersionWithAppendix = Version.VERSION;
    // The version might have an appendix like 2.2.0-SNAPSHOT
    int indexOfMinus = engineVersionWithAppendix.indexOf("-");
    indexOfMinus = indexOfMinus == -1 ? engineVersionWithAppendix.length() : indexOfMinus;
    return engineVersionWithAppendix.substring(0, indexOfMinus);
  }

  private static void printWarning(String fromVersion, String toVersion) {
    String message =
      "\n\n" +
        "================================ WARNING! ================================ \n\n" +
        "Please be aware that you are about to upgrade the Optimize data \n" +
        "schema in Elasticsearch from version %s to %s. \n" +
        "There is no warranty that this upgrade might not break the data \n" +
        "structure in Elasticsearch. Therefore, it is highly recommended to \n" +
        "create a backup of your data in Elasticsearch in case something goes wrong. \n" +
        "\n" +
        "Do you want to proceed? [(y)es/(n)o] \n" +
        "\n" +
        "1. (y)es = I already did a backup and want to proceed. \n" +
        "2. (n)o = Thanks for reminding me, I want to do a backup first. \n" +
        "\n" +
        "Your answer (type your answer and hit enter): ";

    message = String.format(
      message,
      fromVersion,
      toVersion
    );
    System.out.print(message);

    String answer = "";
    while(!ANSWER_OPTIONS_YES.contains(answer)) {
      Scanner console = new Scanner(System.in);
      answer = console.next().trim().toLowerCase();
      System.out.println();
      if (ANSWER_OPTIONS_NO.contains(answer)) {
        System.out.println("The Optimize upgrade was aborted.");
        System.exit(1);
      } else if (!ANSWER_OPTIONS_YES.contains(answer)) {
        String text = "Your answer was '" + answer + "'. The only accepted answers are '(y)es' or '(n)o'. \n" +
          "\n" +
          "Your answer (type your answer and hit enter): ";
        System.out.print(text);
      }
    }
  }
}
