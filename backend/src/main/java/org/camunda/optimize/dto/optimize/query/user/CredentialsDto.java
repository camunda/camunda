package org.camunda.optimize.dto.optimize.query.user;

import java.io.Serializable;

public class CredentialsDto implements Serializable {

  protected String id;
  protected String password;

  public String getId() {
    return id;
  }

  public void setId(String username) {
    this.id = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
