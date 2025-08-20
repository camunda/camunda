package io.camunda.client.api.command;

import io.camunda.client.api.response.DeleteVariableResponse;

public interface VariableDeleteCommandStep1 extends FinalCommandStep<DeleteVariableResponse> {

  VariableDeleteCommandStep1 key(Long key);
}
