package io.camunda.client.api.command;

import io.camunda.client.api.response.UpdateVariableResponse;

public interface VariableUpdateRequestStep1 extends FinalCommandStep<UpdateVariableResponse> {

  VariableUpdateRequestStep1 variable(Long key, Object value);
}
