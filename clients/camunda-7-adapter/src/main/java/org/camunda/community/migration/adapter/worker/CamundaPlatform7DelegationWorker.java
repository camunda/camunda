package org.camunda.community.migration.adapter.worker;

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.common.exception.ZeebeBpmnError;
import java.util.HashMap;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.community.migration.adapter.execution.ZeebeJobDelegateExecution;
import org.camunda.community.migration.adapter.execution.variable.VariableTyper;
import org.camunda.community.migration.adapter.juel.ClassResolver;
import org.camunda.community.migration.adapter.juel.JuelExpressionResolver;
import org.springframework.stereotype.Component;

@Component
public class CamundaPlatform7DelegationWorker {

  private final JuelExpressionResolver expressionResolver;
  private final ClassResolver classResolver;
  private final VariableTyper variableTyper;

  public CamundaPlatform7DelegationWorker(
      JuelExpressionResolver expressionResolver,
      ClassResolver classResolver,
      VariableTyper variableTyper) {
    this.expressionResolver = expressionResolver;
    this.classResolver = classResolver;
    this.variableTyper = variableTyper;
  }

  @JobWorker(type = "camunda-7-adapter", autoComplete = false)
  public void delegateToCamundaPlatformCode(final JobClient client, final ActivatedJob job)
      throws Exception {
    // Read config
    String delegateClass = job.getCustomHeaders().get("class");
    String delegateExpression = job.getCustomHeaders().get("delegateExpression");
    String expression = job.getCustomHeaders().get("expression");
    String resultVariable = job.getCustomHeaders().get("resultVariable");
    String startListener = job.getCustomHeaders().get("executionListener.start");
    String endListener = job.getCustomHeaders().get("executionListener.end");
    // and delegate depending on exact way of implementation

    final DelegateExecution execution = new ZeebeJobDelegateExecution(job, variableTyper);

    try {
      if (delegateClass == null && delegateExpression == null && expression == null) {
        throw new RuntimeException(
            "Either 'class' or 'delegateExpression' or 'expression' must be specified in task headers for job :"
                + job);
      }

      if (startListener != null) {
        ExecutionListener executionListener =
            (ExecutionListener) expressionResolver.evaluate(startListener, execution);

        executionListener.notify(execution);
      }

      if (delegateClass != null) {
        JavaDelegate javaDelegate = classResolver.loadJavaDelegate(delegateClass);
        javaDelegate.execute(execution);
      } else if (delegateExpression != null) {
        JavaDelegate javaDelegate =
            (JavaDelegate) expressionResolver.evaluate(delegateExpression, execution);
        javaDelegate.execute(execution);
      } else if (expression != null) {
        Object result = expressionResolver.evaluate(expression, execution);

        if (resultVariable != null) {
          execution.setVariable(resultVariable, result);
        }
      }

      if (endListener != null) {
        ExecutionListener executionListener =
            (ExecutionListener) expressionResolver.evaluate(endListener, execution);
        executionListener.notify(execution);
      }

      CompleteJobCommandStep1 completeCommand = client.newCompleteCommand(job.getKey());
      completeCommand.variables(execution.getVariables());
      completeCommand.send().join();
    } catch (BpmnError e) {
      throw new ZeebeBpmnError(
          e.getErrorCode(), e.getMessage() == null ? "" : e.getMessage(), new HashMap<>());
    }
  }
}
