package org.camunda.operate.es.writer;

/**
 * @author Svetlana Dorokhova.
 */
public class PersistenceException extends Exception {

  private Integer failingRequestId;

  public PersistenceException() {
  }

  public PersistenceException(String message) {
    super(message);
  }

  public PersistenceException(String message, Throwable cause) {
    super(message, cause);
  }

  public PersistenceException(String message, Throwable cause, Integer failingRequestId) {
    super(message, cause);
    this.failingRequestId = failingRequestId;
  }

  public PersistenceException(Throwable cause) {
    super(cause);
  }

  public Integer getFailingRequestId() {
    return failingRequestId;
  }

  public void setFailingRequestId(Integer failingRequestId) {
    this.failingRequestId = failingRequestId;
  }
}
