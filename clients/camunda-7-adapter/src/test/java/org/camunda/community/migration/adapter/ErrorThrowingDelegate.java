package org.camunda.community.migration.adapter;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("errorThrowingDelegate")
public class ErrorThrowingDelegate implements JavaDelegate {
  @Override
  public void execute(DelegateExecution execution) throws Exception {
    throw new BpmnError("test-error");
  }
}
