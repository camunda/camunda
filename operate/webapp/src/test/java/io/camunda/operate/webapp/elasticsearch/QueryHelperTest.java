package io.camunda.operate.webapp.elasticsearch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.security.permission.PermissionsService.ResourcesAllowed;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.elasticsearch.index.query.QueryBuilder;
import org.junit.jupiter.api.Test;

public class QueryHelperTest {

  @Test
  public void shouldUseIncidentFieldWhenActivityIdAndIncidentsAreSet() {
    final QueryHelper queryHelper = new QueryHelper();
    final String activityId = "testActivityId";

    final PermissionsService permissionsService = mock(PermissionsService.class);
    final ListViewQueryDto queryDto =
        new ListViewQueryDto().setActivityId(activityId).setIncidents(true).setRunning(true);

    queryHelper.setPermissionsService(permissionsService);
    when(permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(ResourcesAllowed.wildcard());

    final QueryBuilder qb = queryHelper.createQueryFragment(queryDto);
    assertNotNull(qb, "QueryBuilder should not be null");
    final String queryString = qb.toString();
    assertTrue(queryString.contains("activityState"), "Should contain activityState field");
    assertTrue(queryString.contains("ACTIVE"), "Should contain activityState=ACTIVE");
    assertTrue(queryString.contains("activityId"), "Should contain activityId field");
    assertTrue(queryString.contains(activityId), "Should contain the provided activityId");
    assertTrue(queryString.contains("incident"), "Should contain incident field");
    assertTrue(queryString.contains("true"), "Should contain incident=true");
    // Ensure error message field is not used
    assertFalse(queryString.contains("errorMessage"), "Should not contain errorMessage field");
  }
}
