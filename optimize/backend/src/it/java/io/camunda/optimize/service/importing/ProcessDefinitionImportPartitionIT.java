/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
// import static io.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import java.util.stream.IntStream;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class ProcessDefinitionImportPartitionIT extends AbstractImportIT {
//
//   @Test
//   public void processDefinitionImportBatchesThatRequirePartitioningCanBeImported() {
//     // given
//     // more definitions than the max ES boolQuery clause limit (1024)
//     final int definitionsToDeploy = 1100;
//     IntStream.range(0, definitionsToDeploy)
//         .forEach(
//             defCount ->
//                 engineIntegrationExtension.deployProcessAndGetProcessDefinition(
//                     getSingleServiceTaskProcess("procName_" + defCount), null));
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//
// assertThat(databaseIntegrationTestExtension.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME))
//         .isEqualTo(definitionsToDeploy);
//   }
// }
