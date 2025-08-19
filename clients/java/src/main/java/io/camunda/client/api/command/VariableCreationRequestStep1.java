package io.camunda.client.api.command;

import io.camunda.client.api.response.CreateVariableResponse;

public interface VariableCreationRequestStep1 extends FinalCommandStep<CreateVariableResponse> {

  VariableCreationRequestStep1 variable(String key, Object value);

  VariableCreationRequestStep1 clusterLevel();
}
