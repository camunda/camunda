package org.camunda.optimize.rest.engine.dto;

/**
 * @author Daniel Meyer
 *
 */
public class UserProfileDto {

  protected String id;
  protected String firstName;
  protected String lastName;
  protected String email;

  // getter / setters ////////////////////////////////////
  
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}
