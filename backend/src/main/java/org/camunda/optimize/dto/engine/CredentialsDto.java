package org.camunda.optimize.dto.engine;

/**
 * @author Askar Akhmerov
 */
public class CredentialsDto {
  protected String username;
  protected String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
