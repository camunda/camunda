package io.camunda.common.auth;

/** Contains credential for particular product. Used for Simple authentication. */
public class SimpleCredential {

  public SimpleCredential(String user, String password) {
    this.user = user;
    this.password = password;
  }

  private String user;
  private String password;

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }
}
