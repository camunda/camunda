/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import io.camunda.optimize.service.db.reader.BranchAnalysisReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class BranchAnalysisReaderOS implements BranchAnalysisReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(BranchAnalysisReaderOS.class);

  public BranchAnalysisReaderOS() {}

  @Override
  public BranchAnalysisResponseDto branchAnalysis(
      final BranchAnalysisRequestDto request, final ZoneId timezone) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }
}
