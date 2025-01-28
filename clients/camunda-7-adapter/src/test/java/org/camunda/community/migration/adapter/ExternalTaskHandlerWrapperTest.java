package org.camunda.community.migration.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.io.Resources;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.response.ActivatedJobImpl;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.community.migration.adapter.worker.ExternalTaskHandlerWrapper;
import org.junit.jupiter.api.Test;

public class ExternalTaskHandlerWrapperTest {

  private static String loadVariableContent() {
    try {
      return Resources.toString(
          Resources.getResource("test-variables.json"), Charset.defaultCharset());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void shouldResolveVariables() {
    JobClient client = mock(JobClient.class);
    ActivatedJob job =
        new ActivatedJobImpl(
            new CamundaObjectMapper(),
            GatewayOuterClass.ActivatedJob.newBuilder()
                .setVariables(loadVariableContent())
                .setCustomHeaders("{}")
                .build());
    ExternalTaskHandlerWrapper wrapper =
        new ExternalTaskHandlerWrapper(
            (externalTask, externalTaskService) -> {
              String stringVar = externalTask.getVariable("stringVar");
              TypedValue typedStringVar = externalTask.getVariableTyped("stringVar");
              assertThat(typedStringVar.getValue()).isEqualTo(stringVar);

              Map<String, Object> objectVar = externalTask.getVariable("objectVar");
              TypedValue typedObjectVar = externalTask.getVariableTyped("objectVar");
              assertThat(typedObjectVar.getValue()).isEqualTo(objectVar);

              Number numberVar = externalTask.getVariable("numberVar");
              TypedValue typedNumberVar = externalTask.getVariableTyped("numberVar");
              assertThat(typedNumberVar.getValue()).isEqualTo(numberVar);

              Boolean booleanVar = externalTask.getVariable("booleanVar");
              TypedValue typedBooleanVar = externalTask.getVariableTyped("booleanVar");
              assertThat(typedBooleanVar.getValue()).isEqualTo(booleanVar);

              List<Object> listVar = externalTask.getVariable("listVar");
              TypedValue typedListVar = externalTask.getVariableTyped("listVar");
              assertThat(typedListVar.getValue()).isEqualTo(listVar);

              Object nullVar = externalTask.getVariable("nullVar");
              TypedValue typedNullVar = externalTask.getVariableTyped("nullVar");
              assertThat(typedNullVar.getValue()).isEqualTo(nullVar);
              String businessKey = externalTask.getBusinessKey();
              assertThat(businessKey).isNotNull().isEqualTo("12345");
            },
            Optional.of("businessKey"));
    wrapper.handle(client, job);
  }
}
