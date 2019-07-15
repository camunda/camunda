/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserEntity extends OperateEntity{

  private String username;
  private String password;
  
  private String roles;

  public String getRoles() {
    return roles;
  }

  public UserEntity setRoles(String roles) {
    this.roles = roles;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public UserEntity setUsername(String username) {
    this.username = username;
    setId(username);
    return this;
  }

  public String getPassword() {
    return password;
  }

  public UserEntity setPassword(String password) {
    this.password = password;
    return this;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((password == null) ? 0 : password.hashCode());
    result = prime * result + ((roles == null) ? 0 : roles.hashCode());
    result = prime * result + ((username == null) ? 0 : username.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    UserEntity other = (UserEntity) obj;
    if (password == null) {
      if (other.password != null)
        return false;
    } else if (!password.equals(other.password))
      return false;
    if (roles == null) {
      if (other.roles != null)
        return false;
    } else if (!roles.equals(other.roles))
      return false;
    if (username == null) {
      if (other.username != null)
        return false;
    } else if (!username.equals(other.username))
      return false;
    return true;
  }

  public static UserEntity fromUserDetails(UserDetails userDetails) {
    UserEntity userEntity = new UserEntity();
    userEntity.setId(userDetails.getUsername());
    userEntity.setUsername(userDetails.getUsername());
    userEntity.setPassword(userDetails.getPassword());
    Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
    if(authorities!=null && !authorities.isEmpty()) {
      userEntity.setRoles(authorities.iterator().next().getAuthority().replace("ROLE_", ""));
    }
    return userEntity;
  }

  public static UserEntity from(String username, String password,String role) {
    UserEntity userEntity = new UserEntity();
    userEntity.setId(username);
    userEntity.setUsername(username);
    userEntity.setPassword(password);
    userEntity.setRoles(role);
    return userEntity;
  }

}
