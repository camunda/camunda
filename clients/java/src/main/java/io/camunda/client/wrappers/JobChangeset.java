package io.camunda.client.wrappers;

public class JobChangeset {

  private Integer retries;
  private Long timeout;

  public Integer getRetries() {
    return retries;
  }

  public JobChangeset setRetries(Integer retries) {
    this.retries = retries;
    return this;
  }

  public Long getTimeout() {
    return timeout;
  }

  public JobChangeset setTimeout(Long timeout) {
    this.timeout = timeout;
    return this;
  }

  public static io.camunda.client.protocol.rest.JobChangeset toProtocolObject(JobChangeset object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.JobChangeset protocolObject =
        new io.camunda.client.protocol.rest.JobChangeset();
    protocolObject.setRetries(object.retries);
    protocolObject.setTimeout(object.timeout);
    return protocolObject;
  }

  public static JobChangeset fromProtocolObject(
      io.camunda.client.protocol.rest.JobChangeset protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final JobChangeset object = new JobChangeset();
    object.retries = protocolObject.getRetries();
    object.timeout = protocolObject.getTimeout();
    return object;
  }
}
