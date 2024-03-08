package io.camunda.zeebe.spring.client.exception;

import java.util.Map;

/**
 * Indicates an error in sense of BPMN occured, that should be handled by the BPMN process, see
 * https://docs.camunda.io/docs/reference/bpmn-processes/error-events/error-events/
 */
public class ZeebeBpmnError extends RuntimeException {

  private String errorCode;
  private String errorMessage;
  private Map<String, Object> variables;

  public ZeebeBpmnError(String errorCode, String errorMessage) {
    super("[" + errorCode + "] " + errorMessage);
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public ZeebeBpmnError(String errorCode, String errorMessage, Map<String, Object> variables) {
    super("[" + errorCode + "] " + errorMessage);
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.variables = variables;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }
}
