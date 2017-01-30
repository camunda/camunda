package org.camunda.optimize.dto.optimize;

import java.io.Serializable;

/**
 * @author Askar Akhmerov
 */
public class CredentialsDto implements Serializable {

  private String username;
  private String password;

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
