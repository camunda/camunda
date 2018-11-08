package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicStatusLine;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EsIndexAdjusterTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock
  private RestClient restClient;

  @Mock
  private ConfigurationService configurationService;

  @Test
  public void testFailOnReindexError() throws IOException {
    // given
    final ESIndexAdjuster underTest = new ESIndexAdjuster(
      restClient, configurationService
    );
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    final Response esResponse = createEsResponse(new ReindexTaskResponse(taskId));
    when(restClient.performRequest(eq("POST"), eq("_reindex"), any(), any(), any())).thenReturn(esResponse);

    // the task response contains an error
    final TaskResponse taskResponseWithError = new TaskResponse(
      true,
      new TaskResponse.Task(taskId, new TaskResponse.Status(1L, 0L, 0L, 0L)),
      new TaskResponse.Error("error", "failed hard", "reindex")
    );
    final Response taskStatusResponse = createEsResponse(taskResponseWithError);
    when(restClient.performRequest(eq("GET"), eq("_tasks/" + taskId))).thenReturn(taskStatusResponse);

    // when I execute a reindex
    UpgradeRuntimeException expectedException = null;
    try {
      underTest.reindex(index1, index2, "type", "type");
      // then the reindex fails with an UpgradeRuntimeException
    } catch (UpgradeRuntimeException e) {
      expectedException = e;
    }
    // and the exception contains the error from the task reponse
    assertThat(expectedException, is(notNullValue()));
    assertThat(expectedException.getMessage(), is(taskResponseWithError.getError().toString()));
  }

  private Response createEsResponse(Object response) throws IOException {
    final ProtocolVersion protocolVersion = new ProtocolVersion("http", 1, 1);

    final Response mockedReindexResponse = mock(Response.class);
    when(mockedReindexResponse.getStatusLine())
      .thenReturn(new BasicStatusLine(protocolVersion, 200, "OK"));

    final HttpEntity httpEntity = mock(HttpEntity.class);
    when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(response)));
    when((mockedReindexResponse.getEntity())).thenReturn(httpEntity);

    return mockedReindexResponse;
  }


}
