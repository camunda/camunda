package org.camunda.optimize.test.performance.data.generation.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.performance.data.generation.DataGenerator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ProcessRequestDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/process-request.bpmn";

  protected BpmnModelInstance retrieveDiagram() {
    try {
      return readDiagramAsInstance(DIAGRAM);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public Set<String> getPathVariableNames() {
    Set<String> variableNames = new HashSet<>();
    variableNames.add("processAvailable");
    variableNames.add("partnerIsNew");
    variableNames.add("isBestehenderPartner");
    variableNames.add("bestehendenPartnerAendern");
    variableNames.add("isAdressaenderung");
    variableNames.add("abweichenderPZVorhanden");
    variableNames.add("andereRollenVorhanden");
    variableNames.add("policeChanged");
    variableNames.add("isSanierungBuendel");
    variableNames.add("portalvertrag");
    variableNames.add("isInkassoNummerFalschFehlt");
    variableNames.add("errorOccured");
    variableNames.add("processInformation");
    variableNames.add("isVorversichererAnfrage");
    variableNames.add("isAusstehendeDokumente");
    variableNames.add("andereRollenVorhanden");
    variableNames.add("abweichenderPZVorhanden");
    return variableNames;
  }

}
