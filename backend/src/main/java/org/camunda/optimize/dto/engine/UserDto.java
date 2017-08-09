package org.camunda.optimize.dto.engine;

public class UserDto {
  private String firstName;
  private String lastName;
  private String displayName;

  private String id;

  public String getFirstName() {
    return firstName;
  }

  public String getId() {
    return id;
  }

  public String getLastName() {
    return lastName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setId(String id) {
    this.id = id;
  }
}
