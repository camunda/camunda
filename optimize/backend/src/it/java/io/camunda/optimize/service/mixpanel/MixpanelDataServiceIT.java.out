/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.mixpanel;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
// import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
// import io.camunda.optimize.service.mixpanel.client.MixpanelEntityEventProperties;
// import io.camunda.optimize.service.mixpanel.client.MixpanelHeartbeatProperties;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import io.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
// import lombok.NonNull;
// import org.elasticsearch.core.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class MixpanelDataServiceIT extends AbstractPlatformIT {
//
//   private static final String CLUSTER_ID = "IT-cluster";
//   private static final String STAGE = "IT";
//   private static final String ORGANIZATION_ID = "orgId";
//
//   @Test
//   public void heartbeatEventData() {
//     // given
//     final MixpanelConfiguration mixpanelConfiguration = getMixpanelConfiguration();
//     mixpanelConfiguration.getProperties().setStage(STAGE);
//     mixpanelConfiguration.getProperties().setOrganizationId(ORGANIZATION_ID);
//     mixpanelConfiguration.getProperties().setClusterId(CLUSTER_ID);
//
//     reportClient.createEmptySingleProcessReport();
//     final String collectionId = collectionClient.createNewCollection();
//     final String reportInCollectionId =
//         reportClient.createEmptySingleProcessReportInCollection(collectionId);
//     reportClient.createEmptySingleDecisionReport();
//     final String dashboardId = dashboardClient.createEmptyDashboard();
//     alertClient.createAlertForReport(reportInCollectionId);
//     final ReportShareRestDto reportShare = new ReportShareRestDto();
//     reportShare.setReportId(reportInCollectionId);
//     sharingClient.createReportShareResponse(reportShare);
//     final DashboardShareRestDto dashboardShare = new DashboardShareRestDto();
//     dashboardShare.setDashboardId(dashboardId);
//     sharingClient.createDashboardShareResponse(dashboardShare);
//     createUserTaskReport();
//
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // when
//     final MixpanelHeartbeatProperties mixpanelHeartbeatProperties =
//         getMixpanelDataService().getMixpanelHeartbeatProperties();
//
//     // then
//     assertThat(mixpanelHeartbeatProperties.getTime())
//         .isLessThanOrEqualTo(System.currentTimeMillis());
//     assertThat(mixpanelHeartbeatProperties.getDistinctId()).isEmpty();
//     assertThat(mixpanelHeartbeatProperties.getInsertId()).isNotEmpty();
//     assertThat(mixpanelHeartbeatProperties.getProduct()).isEqualTo("optimize");
//     assertThat(mixpanelHeartbeatProperties.getStage()).isEqualTo(STAGE);
//     assertThat(mixpanelHeartbeatProperties.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
//     assertThat(mixpanelHeartbeatProperties.getClusterId()).isEqualTo(CLUSTER_ID);
//     // The management reports are not included in the result
//     assertThat(mixpanelHeartbeatProperties.getProcessReportCount()).isEqualTo(3);
//     assertThat(mixpanelHeartbeatProperties.getDecisionReportCount()).isEqualTo(1);
//     // The management dashboard is not included in the result
//     assertThat(mixpanelHeartbeatProperties.getDashboardCount()).isEqualTo(1);
//     assertThat(mixpanelHeartbeatProperties.getReportShareCount()).isEqualTo(1);
//     assertThat(mixpanelHeartbeatProperties.getDashboardShareCount()).isEqualTo(1);
//     assertThat(mixpanelHeartbeatProperties.getAlertCount()).isEqualTo(1);
//     assertThat(mixpanelHeartbeatProperties.getTaskReportCount()).isEqualTo(1);
//   }
//
//   @Test
//   public void entityEventData() {
//     // given
//     final MixpanelConfiguration mixpanelConfiguration = getMixpanelConfiguration();
//     mixpanelConfiguration.getProperties().setStage(STAGE);
//     mixpanelConfiguration.getProperties().setOrganizationId(ORGANIZATION_ID);
//     mixpanelConfiguration.getProperties().setClusterId(CLUSTER_ID);
//
//     // when
//     final String entityId = "id";
//     final MixpanelEntityEventProperties mixpanelEntityEventProperties =
//         getMixpanelDataService().getMixpanelEntityEventProperties(entityId);
//
//     // then
//     assertThat(mixpanelEntityEventProperties.getTime())
//         .isLessThanOrEqualTo(System.currentTimeMillis());
//     assertThat(mixpanelEntityEventProperties.getDistinctId()).isEmpty();
//     assertThat(mixpanelEntityEventProperties.getInsertId()).isNotEmpty();
//     assertThat(mixpanelEntityEventProperties.getProduct()).isEqualTo("optimize");
//     assertThat(mixpanelEntityEventProperties.getStage()).isEqualTo(STAGE);
//     assertThat(mixpanelEntityEventProperties.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
//     assertThat(mixpanelEntityEventProperties.getClusterId()).isEqualTo(CLUSTER_ID);
//     assertThat(mixpanelEntityEventProperties.getEntityId()).isEqualTo(entityId);
//   }
//
//   private MixpanelConfiguration getMixpanelConfiguration() {
//     return embeddedOptimizeExtension.getConfigurationService().getAnalytics().getMixpanel();
//   }
//
//   @NonNull
//   private MixpanelDataService getMixpanelDataService() {
//     return embeddedOptimizeExtension.getBean(MixpanelDataService.class);
//   }
//
//   private void createUserTaskReport() {
//     final ProcessReportDataDto reportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey("aKey")
//             .setProcessDefinitionVersions(List.of("all"))
//             .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
//             .setReportDataType(USER_TASK_DUR_GROUP_BY_ASSIGNEE)
//             .build();
//     reportClient.createSingleProcessReport(new
// SingleProcessReportDefinitionRequestDto(reportData));
//   }
// }
