package org.camunda.optimize.service.security;

public interface SessionListener {
  void onSessionCreateOrRefresh(String userId);
  void onSessionDestroy(String userId);
}
