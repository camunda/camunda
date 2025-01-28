package org.camunda.community.migration.adapter;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampleDelegate implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(SampleDelegate.class);

  public static boolean executed = false;
  public static VariableDto capturedVariable = null;
  public static String capturedBusinessKey = null;
  public static boolean canReachExecutionVariable = false;

  @Override
  public void execute(DelegateExecution execution) {
    LOG.info("Called from process instance {}", execution.getProcessInstanceId());

    capturedVariable = (VariableDto) execution.getVariable("someVariable");
    canReachExecutionVariable = execution.getVariable("execution") != null;
    execution.setProcessBusinessKey("42");
    capturedBusinessKey = execution.getProcessBusinessKey();
    executed = true;
  }
}
