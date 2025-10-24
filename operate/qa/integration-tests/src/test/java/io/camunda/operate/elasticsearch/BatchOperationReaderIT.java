/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.util.TestUtil.createBatchOperationEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.rest.BatchOperationRestService;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.permission.PermissionsService.ResourcesAllowed;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

/** Tests retrieval of batch operation list taking into account BATCH READ permissions. */
public class BatchOperationReaderIT extends OperateAbstractIT {

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @MockitoBean protected PermissionsService operatePermissionsService;
  private final ArrayList<String> allOperationIds = new ArrayList<>();

  private final Comparator<BatchOperationDto> batchOperationEntityComparator =
      (o1, o2) -> {
        if (o2.getEndDate() != null && o1.getEndDate() != null) {
          final int i = o2.getEndDate().compareTo(o1.getEndDate());
          if (i != 0) {
            return i;
          }
        } else if (o2.getEndDate() != null) {
          return -1;
        } else if (o1.getEndDate() != null) {
          return 1;
        }
        return o2.getStartDate().compareTo(o1.getStartDate());
      };

  @Override
  @Before
  public void before() {
    super.before();
    createData();
  }

  @Test
  public void testHasBatchReadPermissions() throws Exception {
    when(operatePermissionsService.getBatchOperationsWithPermission(PermissionType.READ))
        .thenReturn(ResourcesAllowed.wildcard());

    final BatchOperationDto op1 = assert3Pages();

    // finish 1st operation
    final BatchOperationEntity batchOperationEntity =
        createBatchOperationEntity(op1.getStartDate(), OffsetDateTime.now());
    batchOperationEntity.setId(op1.getId());
    searchTestRule.persistNew(batchOperationEntity);
    searchTestRule.refreshOperateSearchIndices();

    final BatchOperationDto op2 = assert3Pages();
    assertThat(op1.getId()).isNotEqualTo(op2.getId());
  }

  private BatchOperationDto assert3Pages() throws Exception {
    final List<BatchOperationDto> page1 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, null, null)), BatchOperationDto.class);
    assertThat(page1).hasSize(2);
    assertThat(page1).isSortedAccordingTo(batchOperationEntityComparator);

    final List<BatchOperationDto> page2 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, page1.get(1).getSortValues(), null)),
            BatchOperationDto.class);
    assertThat(page2).hasSize(2);
    assertThat(page2).isSortedAccordingTo(batchOperationEntityComparator);

    // get again page1, but with searchBefore
    final List<BatchOperationDto> page1WithSearchBefore =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, null, page2.get(0).getSortValues())),
            BatchOperationDto.class);
    assertThat(page1WithSearchBefore).hasSize(2);
    assertThat(page1WithSearchBefore).isSortedAccordingTo(batchOperationEntityComparator);
    assertThat(page1WithSearchBefore).containsExactlyElementsOf(page1);

    final List<BatchOperationDto> page3 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, page2.get(1).getSortValues(), null)),
            BatchOperationDto.class);
    assertThat(page3).hasSize(1);

    page1.addAll(page2);
    page1.addAll(page3);

    assertThat(page1).extracting("id").containsExactlyInAnyOrder(allOperationIds.toArray());
    return page1.get(0);
  }

  @Test
  public void testNoBatchReadPermissions() throws Exception {
    when(operatePermissionsService.getBatchOperationsWithPermission(PermissionType.READ))
        .thenReturn(ResourcesAllowed.withIds(Set.of()));
    final List<BatchOperationDto> page1 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(10, null, null)), BatchOperationDto.class);
    assertThat(page1).isEmpty();
  }

  protected void createData() {

    final List<ExporterEntity> entities = new ArrayList<>();

    final OffsetDateTime now = OffsetDateTime.now();

    entities.add(createBatchOperation(now.minus(5, ChronoUnit.MINUTES), null));
    entities.add(createBatchOperation(now.minus(4, ChronoUnit.MINUTES), null));
    entities.add(createBatchOperation(now.minus(3, ChronoUnit.MINUTES), null));
    entities.add(
        createBatchOperation(
            now.minus(2, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES))); // finished
    entities.add(createBatchOperation(now.minus(1, ChronoUnit.MINUTES), now)); // finished
    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));
  }

  private ExporterEntity createBatchOperation(
      final OffsetDateTime startDate, final OffsetDateTime endDate) {
    final BatchOperationEntity batchOperationEntity =
        createBatchOperationEntity(startDate, endDate);
    allOperationIds.add(batchOperationEntity.getId());
    return batchOperationEntity;
  }

  protected MvcResult postRequest(final Object query) throws Exception {
    return postRequest(BatchOperationRestService.BATCH_OPERATIONS_URL, query);
  }
}
