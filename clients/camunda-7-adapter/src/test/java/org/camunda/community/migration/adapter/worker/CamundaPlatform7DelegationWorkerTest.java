package org.camunda.community.migration.adapter.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobResult;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.worker.JobClient;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.community.migration.adapter.execution.variable.VariableTyper;
import org.camunda.community.migration.adapter.juel.ClassResolver;
import org.camunda.community.migration.adapter.juel.JuelExpressionResolver;
import org.junit.jupiter.api.Test;

class CamundaPlatform7DelegationWorkerTest {

  private final ActivatedJob job = mock(ActivatedJob.class);
  private final JobClient client = mock(JobClient.class, RETURNS_DEEP_STUBS);
  private final JuelExpressionResolver juelExpressionResolver = mock(JuelExpressionResolver.class);
  private final ClassResolver classResolver = mock(ClassResolver.class);

  private final CamundaPlatform7DelegationWorker worker =
      new CamundaPlatform7DelegationWorker(
          juelExpressionResolver, classResolver, new VariableTyper(Collections.emptySet()));

  @Test // TODO do we need to test end
  void shouldExecuteExecutionListenerOnStart() throws Exception {
    long jobKey = 0L;
    Map<String, String> headers = new HashMap<>();
    headers.put("delegateExpression", "${myDelegate}");
    headers.put("executionListener.start", "${myStartListener}");

    ActivatedJob job = job(headers, Variables.putValue("hello", "World").putValue("value", 1));

    when(juelExpressionResolver.evaluate(eq("${myStartListener}"), any(DelegateExecution.class)))
        .thenReturn(
            (ExecutionListener)
                execution -> {
                  int value = (int) execution.getVariable("value");
                  execution.setVariable("value", value + 41);
                });
    when(juelExpressionResolver.evaluate(eq("${myDelegate}"), any(DelegateExecution.class)))
        .thenReturn(
            (JavaDelegate)
                execution -> {
                  int value = (int) execution.getVariable("value");
                  execution.setVariable("value", value * 2);
                });

    JobCommandStepFake fake = new JobCommandStepFake();
    when(client.newCompleteCommand(jobKey)).thenReturn(fake);

    worker.delegateToCamundaPlatformCode(client, job);

    assertThat(fake.variables.get("value")).isEqualTo(84);
  }

  private ActivatedJob job(Map<String, String> customHeaders, Map<String, Object> variables) {
    return new ActivatedJob() {
      @Override
      public long getKey() {
        return 0;
      }

      @Override
      public String getType() {
        return null;
      }

      @Override
      public long getProcessInstanceKey() {
        return 0;
      }

      @Override
      public String getBpmnProcessId() {
        return null;
      }

      @Override
      public int getProcessDefinitionVersion() {
        return 0;
      }

      @Override
      public long getProcessDefinitionKey() {
        return 0;
      }

      @Override
      public String getElementId() {
        return null;
      }

      @Override
      public long getElementInstanceKey() {
        return 0;
      }

      @Override
      public Map<String, String> getCustomHeaders() {
        return customHeaders;
      }

      @Override
      public String getWorker() {
        return null;
      }

      @Override
      public int getRetries() {
        return 0;
      }

      @Override
      public long getDeadline() {
        return 0;
      }

      @Override
      public String getVariables() {
        return null;
      }

      @Override
      public Map<String, Object> getVariablesAsMap() {
        return variables;
      }

      @Override
      public <T> T getVariablesAsType(Class<T> variableType) {
        return null;
      }

      @Override
      public Object getVariable(String name) {
        return variables.get(name);
      }

      @Override
      public String toJson() {
        return null;
      }

      @Override
      public String getTenantId() {
        return null;
      }
    };
  }

  private static class JobCommandStepFake implements CompleteJobCommandStep1 {
    public Map<String, Object> variables;

    @Override
    public CompleteJobCommandStep1 variables(InputStream variables) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompleteJobCommandStep1 variables(String variables) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompleteJobCommandStep1 variables(Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public CompleteJobCommandStep1 variables(Object variables) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompleteJobCommandStep1 variable(String key, Object value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FinalCommandStep<CompleteJobResponse> requestTimeout(Duration requestTimeout) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CamundaFuture<CompleteJobResponse> send() {
      return mock(CamundaFuture.class);
    }

    @Override
    public CompleteJobCommandStep1 useRest() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompleteJobCommandStep1 useGrpc() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompleteJobCommandStep2 withResult() {
      return null;
    }

    @Override
    public CompleteJobCommandStep1 withResult(CompleteJobResult jobResult) {
      return null;
    }

    @Override
    public CompleteJobCommandStep1 withResult(UnaryOperator<CompleteJobResult> jobResultModifier) {
      return null;
    }
  }
}
