/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.util.TestUtil.createBatchOperationEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.webapp.rest.BatchOperationRestService;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

  private ArrayList<String> user1OperationIds = new ArrayList<>();
  private ArrayList<String> user2OperationIds = new ArrayList<>();

  private Comparator<BatchOperationDto> batchOperationEntityComparator =
      (o1, o2) -> {
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

    // finish 1st operation
    BatchOperationEntity batchOperationEntity =
        createBatchOperationEntity(op1.getStartDate(), OffsetDateTime.now(), USER_1);
    batchOperationEntity.setId(op1.getId());
    searchTestRule.persistNew(batchOperationEntity);
    searchTestRule.refreshOperateSearchIndices();

    BatchOperationDto op2 = assert3Pages();
    assertThat(op1.getId()).isNotEqualTo(op2.getId());
  }

  private BatchOperationDto assert3Pages() throws Exception {
    List<BatchOperationDto> page1 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, null, null)), BatchOperationDto.class);
    assertThat(page1).hasSize(2);
    assertThat(page1).isSortedAccordingTo(batchOperationEntityComparator);

    List<BatchOperationDto> page2 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, page1.get(1).getSortValues(), null)),
            BatchOperationDto.class);
    assertThat(page2).hasSize(2);
    assertThat(page2).isSortedAccordingTo(batchOperationEntityComparator);

    // get again page1, but with searchBefore
    List<BatchOperationDto> page1_2 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, null, page2.get(0).getSortValues())),
            BatchOperationDto.class);
    assertThat(page1_2).hasSize(2);
    assertThat(page1_2).isSortedAccordingTo(batchOperationEntityComparator);
    assertThat(page1_2).containsExactlyElementsOf(page1);

    List<BatchOperationDto> page3 =
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
    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(USER_2));

    List<BatchOperationDto> page1 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, null, null)), BatchOperationDto.class);
    assertThat(page1).hasSize(2);
    assertThat(page1).isSortedAccordingTo(batchOperationEntityComparator);

    List<BatchOperationDto> page2 =
        mockMvcTestRule.listFromResponse(
            postRequest(new BatchOperationRequestDto(2, page1.get(1).getSortValues(), null)),
            BatchOperationDto.class);
    assertThat(page2).hasSize(0);

    assertThat(page1).extracting("id").containsExactlyInAnyOrder(user2OperationIds.toArray());
  }

  protected void createData() {

    List<OperateEntity> entities = new ArrayList<>();

    OffsetDateTime now = OffsetDateTime.now();

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

    searchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));
  }

  private OperateEntity createBatchOperation(
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      String username,
      ArrayList<String> userList) {
    BatchOperationEntity batchOperationEntity =
        createBatchOperationEntity(startDate, endDate, username);
    userList.add(batchOperationEntity.getId());
    return batchOperationEntity;
  }

  protected MvcResult postRequest(Object query) throws Exception {
    return postRequest(BatchOperationRestService.BATCH_OPERATIONS_URL, query);
  }
}
