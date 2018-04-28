package org.camunda.optimize.dto.optimize.query;

/**
 * @author Askar Akhmerov
 */
public class EngineCredentialsDto extends CredentialsDto {

  protected String engineAlias;

  public EngineCredentialsDto(CredentialsDto credentials, String engine) {
    this.username = credentials.username;
    this.password = credentials.password;
    this.engineAlias = engine;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  public void setEngineAlias(String engineAlias) {
    this.engineAlias = engineAlias;
  }
}
