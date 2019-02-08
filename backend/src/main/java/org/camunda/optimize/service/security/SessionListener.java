package org.camunda.optimize.service.security;

public interface SessionListener {
  void onSessionCreate(String userId);
  void onSessionDestroy(String userId);
}
