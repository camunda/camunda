/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.metadata;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockserver.model.HttpRequest.request;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.Main;
// import io.camunda.optimize.dto.optimize.query.MetadataDto;
// import io.camunda.optimize.service.db.schema.index.MetadataIndex;
// import java.util.Optional;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.mockserver.integration.ClientAndServer;
// import org.mockserver.model.HttpResponse;
// import org.mockserver.model.HttpStatusCode;
// import org.springframework.boot.SpringApplication;
// import org.springframework.context.ConfigurableApplicationContext;
//
// @Tag(OPENSEARCH_PASSING)
// public class DatabaseMetadataVersionIT extends AbstractPlatformIT {
//
//   private static final String SCHEMA_VERSION = "testVersion";
//   private static final String INSTALLATION_ID = "testId";
//
//   @Test
//   public void verifyVersionAndInstallationIdIsInitialized() {
//     // when
//     startAndUseNewOptimizeInstance();
//
//     // then schemaversion matches expected version and installationID is present
//     final Optional<MetadataDto> metadataDto = getMetadataDto();
//     final String expectedVersion =
//         embeddedOptimizeExtension.getBean(PlatformOptimizeVersionService.class).getVersion();
//
//     assertThat(metadataDto)
//         .isPresent()
//         .get()
//         .satisfies(
//             metadata -> {
//               assertThat(metadata.getSchemaVersion()).isEqualTo(expectedVersion);
//               assertThat(metadata.getInstallationId()).isNotNull();
//             });
//   }
//
//   @Test
//   public void verifyNotStartingIfVersionDoesNotMatch() {
//     databaseIntegrationTestExtension.deleteAllOptimizeData();
//
//     final MetadataDto meta = new MetadataDto(SCHEMA_VERSION, INSTALLATION_ID);
//     databaseIntegrationTestExtension.addEntryToDatabase(
//         METADATA_INDEX_NAME, MetadataIndex.ID, meta);
//     assertThatThrownBy(
//             () -> {
//               final ConfigurableApplicationContext context = SpringApplication.run(Main.class);
//               context.close();
//             })
//         .cause()
//         .cause()
//         .hasMessageContaining("The database Optimize schema version [" + SCHEMA_VERSION + "]");
//
//     databaseIntegrationTestExtension.deleteAllOptimizeData();
//   }
//
//   @Test
//   public void verifyGetMetadataFailsOnClientException() {
//     // given
//     final ClientAndServer dbMockServer = useAndGetDbMockServer();
//     dbMockServer
//         .when(request().withPath("/.*-" + METADATA_INDEX_NAME + ".*/_doc/" + MetadataIndex.ID))
//         .respond(
//             HttpResponse.response()
//                 .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500.code()));
//
//     assertThatThrownBy(this::getMetadataDto)
//         .hasMessage("Failed retrieving the Optimize metadata document from database!");
//   }
//
//   private Optional<MetadataDto> getMetadataDto() {
//     return databaseIntegrationTestExtension.readMetadata();
//   }
// }
