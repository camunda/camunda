package org.camunda.community.migration.adapter;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("sampleBean")
public class SampleBean {
  private static final Logger LOG = LoggerFactory.getLogger(SampleBean.class);
  public static boolean executionReceived = false;
  public static boolean someVariableReceived = false;

  public void doStuff(DelegateExecution execution, String someVariable) {
    executionReceived = execution != null;
    someVariableReceived = someVariable != null;
    LOG.info("Called from process instance {}", execution.getProcessInstanceId());
  }
}
