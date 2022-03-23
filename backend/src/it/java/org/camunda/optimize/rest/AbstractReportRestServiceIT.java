/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;

import java.time.OffsetDateTime;

public abstract class AbstractReportRestServiceIT extends AbstractIT {

  protected static final String PROCESS_DEFINITION_KEY = "simple";
  protected static final String DECISION_DEFINITION_KEY = "invoiceClassification";
  protected static final String RANDOM_KEY = "someRandomKey";
  protected static final String RANDOM_VERSION = "someRandomVersion";
  protected static final String RANDOM_STRING = "something";

  protected String addReportToOptimizeWithDefinitionAndRandomXml(final ReportType reportType) {
    switch (reportType) {
      case PROCESS:
        ProcessReportDataDto processReportDataDto = TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setProcessDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        processReportDataDto.getConfiguration().setXml(RANDOM_STRING);
        return addSingleProcessReportWithDefinition(processReportDataDto, null);
      case DECISION:
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        decisionReportDataDto.getConfiguration().setXml(RANDOM_STRING);
        return addSingleDecisionReportWithDefinition(decisionReportDataDto, null);
    }
    return null;
  }

  protected String addSingleProcessReportWithDefinition(final ProcessReportDataDto processReportDataDto) {
    return addSingleProcessReportWithDefinition(processReportDataDto, null);
  }

  protected String addSingleProcessReportWithDefinition(final ProcessReportDataDto processReportDataDto,
                                                        final String collectionId) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(processReportDataDto);
    singleProcessReportDefinitionDto.setId(RANDOM_STRING);
    singleProcessReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleProcessReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner(RANDOM_STRING);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  protected String addSingleDecisionReportWithDefinition(final DecisionReportDataDto decisionReportDataDto) {
    return addSingleDecisionReportWithDefinition(decisionReportDataDto, null);
  }


  protected String addSingleDecisionReportWithDefinition(final DecisionReportDataDto decisionReportDataDto,
                                                         final String collectionId) {
    SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionRequestDto();
    singleDecisionReportDefinitionDto.setData(decisionReportDataDto);
    singleDecisionReportDefinitionDto.setId(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleDecisionReportDefinitionDto.setCreated(someDate);
    singleDecisionReportDefinitionDto.setLastModified(someDate);
    singleDecisionReportDefinitionDto.setOwner(RANDOM_STRING);
    singleDecisionReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

}
