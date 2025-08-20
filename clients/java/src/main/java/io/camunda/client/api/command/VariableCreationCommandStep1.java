package io.camunda.client.api.command;

import io.camunda.client.api.response.CreateVariableResponse;

public interface VariableCreationCommandStep1 extends FinalCommandStep<CreateVariableResponse> {

  VariableCreationCommandStep1 variable(String key, Object value);

  VariableCreationCommandStep1 clusterLevel();
}
