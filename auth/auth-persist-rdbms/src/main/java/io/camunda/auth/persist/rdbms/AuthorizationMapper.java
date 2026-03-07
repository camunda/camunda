/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis mapper for the AUTH_AUTHORIZATION table. */
@Mapper
public interface AuthorizationMapper {

  @Select(
      "SELECT * FROM AUTH_AUTHORIZATION "
          + "WHERE OWNER_ID = #{ownerId} AND OWNER_TYPE = #{ownerType}")
  @Results({
    @Result(column = "AUTHORIZATION_KEY", property = "authorizationKey"),
    @Result(column = "OWNER_ID", property = "ownerId"),
    @Result(column = "OWNER_TYPE", property = "ownerType"),
    @Result(column = "RESOURCE_TYPE", property = "resourceType"),
    @Result(column = "RESOURCE_ID", property = "resourceId"),
    @Result(column = "PERMISSION_TYPES", property = "permissionTypes")
  })
  List<AuthorizationEntity> findByOwner(
      @Param("ownerId") String ownerId, @Param("ownerType") String ownerType);

  @Select(
      "SELECT * FROM AUTH_AUTHORIZATION "
          + "WHERE OWNER_ID = #{ownerId} AND OWNER_TYPE = #{ownerType} "
          + "AND RESOURCE_TYPE = #{resourceType}")
  @Results({
    @Result(column = "AUTHORIZATION_KEY", property = "authorizationKey"),
    @Result(column = "OWNER_ID", property = "ownerId"),
    @Result(column = "OWNER_TYPE", property = "ownerType"),
    @Result(column = "RESOURCE_TYPE", property = "resourceType"),
    @Result(column = "RESOURCE_ID", property = "resourceId"),
    @Result(column = "PERMISSION_TYPES", property = "permissionTypes")
  })
  List<AuthorizationEntity> findByOwnerAndResourceType(
      @Param("ownerId") String ownerId,
      @Param("ownerType") String ownerType,
      @Param("resourceType") String resourceType);

  @Insert(
      "INSERT INTO AUTH_AUTHORIZATION ("
          + "AUTHORIZATION_KEY, OWNER_ID, OWNER_TYPE, RESOURCE_TYPE, RESOURCE_ID, PERMISSION_TYPES"
          + ") VALUES ("
          + "#{authorizationKey}, #{ownerId}, #{ownerType}, #{resourceType}, "
          + "#{resourceId}, #{permissionTypes})")
  void insert(AuthorizationEntity entity);

  @Update(
      "UPDATE AUTH_AUTHORIZATION SET "
          + "OWNER_ID = #{ownerId}, "
          + "OWNER_TYPE = #{ownerType}, "
          + "RESOURCE_TYPE = #{resourceType}, "
          + "RESOURCE_ID = #{resourceId}, "
          + "PERMISSION_TYPES = #{permissionTypes} "
          + "WHERE AUTHORIZATION_KEY = #{authorizationKey}")
  int update(AuthorizationEntity entity);

  @Delete("DELETE FROM AUTH_AUTHORIZATION WHERE AUTHORIZATION_KEY = #{authorizationKey}")
  void deleteByKey(@Param("authorizationKey") long authorizationKey);
}
