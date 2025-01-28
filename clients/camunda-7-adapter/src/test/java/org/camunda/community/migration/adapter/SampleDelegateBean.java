package org.camunda.community.migration.adapter;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("delegateBean")
public class SampleDelegateBean implements JavaDelegate {

  private static final Logger LOG = LoggerFactory.getLogger(SampleDelegateBean.class);

  public static boolean executed = false;
  public static String capturedVariable = null;
  public static String capturedBusinessKey = null;
  public static boolean canReachExecutionVariable = false;

  @Override
  public void execute(DelegateExecution execution) {
    LOG.info("Called from process instance {}", execution.getProcessInstanceId());

    capturedVariable = (String) execution.getVariable("someVariable");
    canReachExecutionVariable = execution.getVariable("execution") != null;
    execution.setProcessBusinessKey("42");
    capturedBusinessKey = execution.getProcessBusinessKey();
    executed = true;
  }
}
