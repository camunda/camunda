package io.camunda.client.api.command;

import io.camunda.client.api.response.CreateGlobalVariableResponse;

public interface GlobalVariableCreationRequestStep1
    extends FinalCommandStep<CreateGlobalVariableResponse> {

  GlobalVariableCreationRequestStep1 variable(String key, Object value);
}
