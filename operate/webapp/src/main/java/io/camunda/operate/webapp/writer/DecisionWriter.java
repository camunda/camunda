/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.writer;

import java.io.IOException;

public interface DecisionWriter {

  long deleteDecisionRequirements(long decisionRequirementsKey) throws IOException;

  long deleteDecisionDefinitionsFor(long decisionRequirementsKey) throws IOException;

  long deleteDecisionInstancesFor(long decisionRequirementsKey) throws IOException;
}
