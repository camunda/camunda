package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.ReindexStep;

/**
 * @author Askar Akhmerov
 */
public class ChangeFieldTypeStep implements ReindexStep {
  private final String initialIndexName;
  private final String finalIndexName;
  private final String mappingAndSettings;
  private final String mappingScript;
  public static final String NAME = "field-type-change";

  public ChangeFieldTypeStep(
    String initialIndexName,
    String finalIndexName,
    String mappingAndSettings,
    String mappingScript
  ) {
    this.initialIndexName = initialIndexName;
    this.finalIndexName = finalIndexName;
    this.mappingAndSettings = mappingAndSettings;
    this.mappingScript = mappingScript;
  }

  public String getInitialIndexName() {
    return initialIndexName;
  }

  public String getFinalIndexName() {
    return finalIndexName;
  }

  public String getMappingAndSettings() {
    return mappingAndSettings;
  }

  public String getMappingScript() {
    return mappingScript;
  }

  public String getName() {
    return NAME;
  }
}
