package org.camunda.optimize.rest.engine.dto;

/**
 * @author Daniel Meyer
 * 
 */
public class UserDto {

  protected UserProfileDto profile;

  protected UserCredentialsDto credentials;


  // getters / setters /////////////////////////////
  
  public UserProfileDto getProfile() {
    return profile;
  }

  public void setProfile(UserProfileDto profile) {
    this.profile = profile;
  }

  public UserCredentialsDto getCredentials() {
    return credentials;
  }

  public void setCredentials(UserCredentialsDto credentials) {
    this.credentials = credentials;
  }

}
