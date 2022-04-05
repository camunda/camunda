/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es;

import io.camunda.operate.webapp.rest.dto.UserDto;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.webapp.rest.BatchOperationRestService;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.util.TestUtil.createBatchOperationEntity;
import static org.mockito.Mockito.when;

/**
 * Tests retrieval of batch operation list taking into account current user name.
 */
public class BatchOperationReaderIT extends OperateIntegrationTest {

  public static final String USER_1 = "user1";
  public static final String USER_2 = "user2";

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  private ArrayList<String> user1OperationIds = new ArrayList<>();
  private ArrayList<String> user2OperationIds = new ArrayList<>();

  private Comparator<BatchOperationDto> batchOperationEntityComparator = (o1, o2) -> {
    if (o2.getEndDate() != null && o1.getEndDate() != null) {
      int i = o2.getEndDate().compareTo(o1.getEndDate());
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

  @Before
  public void before() {
    super.before();
    createData();
  }

  @Test
  public void testUser1Operations() throws Exception {
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(USER_1));

    BatchOperationDto op1 = assert3Pages();

    //finish 1st operation
    BatchOperationEntity batchOperationEntity = createBatchOperationEntity(op1.getStartDate(), OffsetDateTime.now(), USER_1);
    batchOperationEntity.setId(op1.getId());
    elasticsearchTestRule.persistNew(batchOperationEntity);
    elasticsearchTestRule.refreshOperateESIndices();

    BatchOperationDto op2 = assert3Pages();
    assertThat(op1.getId()).isNotEqualTo(op2.getId());
  }

  private BatchOperationDto assert3Pages() throws Exception {
    List<BatchOperationDto> page1 = mockMvcTestRule
        .listFromResponse(postRequest(new BatchOperationRequestDto(2, null, null)), BatchOperationDto.class);
    assertThat(page1).hasSize(2);
    assertThat(page1).isSortedAccordingTo(batchOperationEntityComparator);

    List<BatchOperationDto> page2 = mockMvcTestRule
        .listFromResponse(postRequest(new BatchOperationRequestDto(2, page1.get(1).getSortValues(), null)), BatchOperationDto.class);
    assertThat(page2).hasSize(2);
    assertThat(page2).isSortedAccordingTo(batchOperationEntityComparator);

    //get again page1, but with searchBefore
    List<BatchOperationDto> page1_2 = mockMvcTestRule
        .listFromResponse(postRequest(new BatchOperationRequestDto(2, null, page2.get(0).getSortValues())), BatchOperationDto.class);
    assertThat(page1_2).hasSize(2);
    assertThat(page1_2).isSortedAccordingTo(batchOperationEntityComparator);
    assertThat(page1_2).containsExactlyElementsOf(page1);

    List<BatchOperationDto> page3 = mockMvcTestRule
        .listFromResponse(postRequest(new BatchOperationRequestDto(2, page2.get(1).getSortValues(), null)), BatchOperationDto.class);
    assertThat(page3).hasSize(1);

    page1.addAll(page2);
    page1.addAll(page3);

    assertThat(page1).extracting("id").containsExactlyInAnyOrder(user1OperationIds.toArray());
    return page1.get(0);
  }

  @Test
  public void testUser2Operations() throws Exception {
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(USER_2));

    List<BatchOperationDto> page1 = mockMvcTestRule
        .listFromResponse(postRequest(new BatchOperationRequestDto(2, null, null)), BatchOperationDto.class);
    assertThat(page1).hasSize(2);
    assertThat(page1).isSortedAccordingTo(batchOperationEntityComparator);

    List<BatchOperationDto> page2 = mockMvcTestRule
        .listFromResponse(postRequest(new BatchOperationRequestDto(2, page1.get(1).getSortValues(), null)), BatchOperationDto.class);
    assertThat(page2).hasSize(0);

    assertThat(page1).extracting("id").containsExactlyInAnyOrder(user2OperationIds.toArray());
  }

  protected void createData() {

    List<OperateEntity> entities = new ArrayList<>();

    OffsetDateTime now = OffsetDateTime.now();

    entities.add(createBatchOperation(now.minus(5, ChronoUnit.MINUTES), null, USER_1, user1OperationIds));
    entities.add(createBatchOperation(now.minus(4, ChronoUnit.MINUTES), null, USER_1, user1OperationIds));
    entities.add(createBatchOperation(now.minus(3, ChronoUnit.MINUTES), null, USER_1, user1OperationIds));
    entities.add(createBatchOperation(now.minus(2, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES), USER_1, user1OperationIds));  //finished
    entities.add(createBatchOperation(now.minus(1, ChronoUnit.MINUTES), now, USER_1, user1OperationIds));                               //finished

    entities.add(createBatchOperation(now.minus(5, ChronoUnit.MINUTES), null, USER_2, user2OperationIds));
    entities.add(createBatchOperation(now.minus(4, ChronoUnit.MINUTES), now.minus(3, ChronoUnit.MINUTES), USER_2, user2OperationIds));  //finished

    elasticsearchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

  }

  private OperateEntity createBatchOperation(OffsetDateTime startDate, OffsetDateTime endDate, String username, ArrayList<String> userList) {
    BatchOperationEntity batchOperationEntity = createBatchOperationEntity(startDate, endDate, username);
    userList.add(batchOperationEntity.getId());
    return batchOperationEntity;
  }

  protected MvcResult postRequest(Object query) throws Exception {
    return postRequest(BatchOperationRestService.BATCH_OPERATIONS_URL, query);
  }

}
