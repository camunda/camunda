package org.camunda.optimize.dto.optimize.query.user;

public class OptimizeUserWithPassword extends OptimizeUserDto {

  private String password;

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
