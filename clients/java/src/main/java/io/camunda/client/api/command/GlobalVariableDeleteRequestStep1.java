package io.camunda.client.api.command;

import io.camunda.client.api.response.DeleteGlobalVariableResponse;

public interface GlobalVariableDeleteRequestStep1
    extends FinalCommandStep<DeleteGlobalVariableResponse> {

  GlobalVariableDeleteRequestStep1 name(String name);
}
