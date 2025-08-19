package io.camunda.client.api.command;

import io.camunda.client.api.response.DeleteVariableResponse;

public interface VariableDeleteRequestStep1 extends FinalCommandStep<DeleteVariableResponse> {

  VariableDeleteRequestStep1 key(Long key);
}
