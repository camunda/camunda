package io.camunda.client.api.command;

import io.camunda.client.api.response.UpdateGlobalVariableResponse;

public interface GlobalVariableUpdateRequestStep1
    extends FinalCommandStep<UpdateGlobalVariableResponse> {

  GlobalVariableUpdateRequestStep1 variable(String key, Object value);
}
