package org.camunda.optimize.upgrade;

import org.camunda.optimize.upgrade.service.UpgradeService;
import org.camunda.optimize.upgrade.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Askar Akhmerov
 */
public class Main {

  protected static Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    try {
      ValidationService validationService = new ValidationService();
      validationService.validate();
      UpgradePlan toExecute = new UpgradePlan();
      UpgradeService executor = new UpgradeService(toExecute, args, validationService);
      executor.execute();
      logger.debug("finished upgrade execution");
      System.exit(0);
    } catch (Exception e) {
      logger.error("error while executing upgrade", e);
      System.exit(2);
    }
  }
}
