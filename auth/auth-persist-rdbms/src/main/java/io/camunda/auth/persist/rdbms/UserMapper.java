/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis mapper for the AUTH_USER table. */
@Mapper
public interface UserMapper {

  @Select("SELECT * FROM AUTH_USER WHERE USERNAME = #{username}")
  @Results({
    @Result(column = "USER_KEY", property = "userKey"),
    @Result(column = "USERNAME", property = "username"),
    @Result(column = "NAME", property = "name"),
    @Result(column = "EMAIL", property = "email"),
    @Result(column = "PASSWORD", property = "password")
  })
  UserEntity findByUsername(@Param("username") String username);

  @Select("SELECT * FROM AUTH_USER WHERE USER_KEY = #{userKey}")
  @Results({
    @Result(column = "USER_KEY", property = "userKey"),
    @Result(column = "USERNAME", property = "username"),
    @Result(column = "NAME", property = "name"),
    @Result(column = "EMAIL", property = "email"),
    @Result(column = "PASSWORD", property = "password")
  })
  UserEntity findByKey(@Param("userKey") long userKey);

  @Insert(
      "INSERT INTO AUTH_USER ("
          + "USER_KEY, USERNAME, NAME, EMAIL, PASSWORD"
          + ") VALUES ("
          + "#{userKey}, #{username}, #{name}, #{email}, #{password})")
  void insert(UserEntity entity);

  @Update(
      "UPDATE AUTH_USER SET "
          + "USERNAME = #{username}, "
          + "NAME = #{name}, "
          + "EMAIL = #{email}, "
          + "PASSWORD = #{password} "
          + "WHERE USER_KEY = #{userKey}")
  int update(UserEntity entity);

  @Delete("DELETE FROM AUTH_USER WHERE USERNAME = #{username}")
  void deleteByUsername(@Param("username") String username);
}
