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
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Tests retrieval of batch operation list taking into account current user name. */
public class BatchOperationReaderIT extends OperateAbstractIT {

  public static final String USER_1 = "user1";
  public static final String USER_2 = "user2";

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();

  private final ArrayList<String> user1OperationIds = new ArrayList<>();
  private final ArrayList<String> user2OperationIds = new ArrayList<>();

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
  public void testUser1Operations() throws Exception {
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(
            new CamundaAuthentication(
                USER_1,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()));

    final BatchOperationDto op1 = assert3Pages();

    // finish 1st operation
    final BatchOperationEntity batchOperationEntity =
        createBatchOperationEntity(op1.getStartDate(), OffsetDateTime.now(), USER_1);
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

    assertThat(page1).extracting("id").containsExactlyInAnyOrder(user1OperationIds.toArray());
    return page1.get(0);
  }

  @Test
  public void testUser2Operations() throws Exception {
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(
            new CamundaAuthentication(
                USER_2,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap()));
    final List<BatchOperationDto> page1 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, null, null)), BatchOperationDto.class);
    assertThat(page1).hasSize(2);
    assertThat(page1).isSortedAccordingTo(batchOperationEntityComparator);

    final List<BatchOperationDto> page2 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, page1.get(1).getSortValues(), null)),
            BatchOperationDto.class);
    assertThat(page2).hasSize(0);

    assertThat(page1).extracting("id").containsExactlyInAnyOrder(user2OperationIds.toArray());
  }

  protected void createData() {

    final List<ExporterEntity> entities = new ArrayList<>();

    final OffsetDateTime now = OffsetDateTime.now();

    entities.add(
        createBatchOperation(now.minus(5, ChronoUnit.MINUTES), null, USER_1, user1OperationIds));
    entities.add(
        createBatchOperation(now.minus(4, ChronoUnit.MINUTES), null, USER_1, user1OperationIds));
    entities.add(
        createBatchOperation(now.minus(3, ChronoUnit.MINUTES), null, USER_1, user1OperationIds));
    entities.add(
        createBatchOperation(
            now.minus(2, ChronoUnit.MINUTES),
            now.minus(1, ChronoUnit.MINUTES),
            USER_1,
            user1OperationIds)); // finished
    entities.add(
        createBatchOperation(
            now.minus(1, ChronoUnit.MINUTES), now, USER_1, user1OperationIds)); // finished

    entities.add(
        createBatchOperation(now.minus(5, ChronoUnit.MINUTES), null, USER_2, user2OperationIds));
    entities.add(
        createBatchOperation(
            now.minus(4, ChronoUnit.MINUTES),
            now.minus(3, ChronoUnit.MINUTES),
            USER_2,
            user2OperationIds)); // finished

    searchTestRule.persistNew(entities.toArray(new ExporterEntity[entities.size()]));
  }

  private ExporterEntity createBatchOperation(
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final String username,
      final ArrayList<String> userList) {
    final BatchOperationEntity batchOperationEntity =
        createBatchOperationEntity(startDate, endDate, username);
    userList.add(batchOperationEntity.getId());
    return batchOperationEntity;
  }

  protected MvcResult postRequest(final Object query) throws Exception {
    return postRequest(BatchOperationRestService.BATCH_OPERATIONS_URL, query);
  }
}
