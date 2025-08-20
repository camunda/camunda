package io.camunda.client.api.command;

import io.camunda.client.api.response.UpdateVariableResponse;

public interface VariableUpdateCommandStep1 extends FinalCommandStep<UpdateVariableResponse> {

  VariableUpdateCommandStep1 variable(Long key, Object value);
}
