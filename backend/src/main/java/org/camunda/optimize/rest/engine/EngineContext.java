package org.camunda.optimize.rest.engine;

import javax.ws.rs.client.Client;

/**
 * @author Askar Akhmerov
 */
public class EngineContext {

  private String engineAlias;
  private Client engineClient;

  public Client getEngineClient() {
    return engineClient;
  }

  public void setEngineClient(Client engineClient) {
    this.engineClient = engineClient;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public void setEngineAlias(String engineAlias) {
    this.engineAlias = engineAlias;
  }
}
