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

/** MyBatis mapper for the AUTH_ROLE and AUTH_ROLE_MEMBER tables. */
@Mapper
public interface RoleMapper {

  @Select("SELECT * FROM AUTH_ROLE WHERE ROLE_ID = #{roleId}")
  @Results({
    @Result(column = "ROLE_KEY", property = "roleKey"),
    @Result(column = "ROLE_ID", property = "roleId"),
    @Result(column = "NAME", property = "name"),
    @Result(column = "DESCRIPTION", property = "description")
  })
  RoleEntity findById(@Param("roleId") String roleId);

  @Select(
      "SELECT r.ROLE_KEY, r.ROLE_ID, r.NAME, r.DESCRIPTION "
          + "FROM AUTH_ROLE r "
          + "INNER JOIN AUTH_ROLE_MEMBER m ON r.ROLE_ID = m.ROLE_ID "
          + "WHERE m.MEMBER_ID = #{memberId} AND m.MEMBER_TYPE = #{memberType}")
  @Results({
    @Result(column = "ROLE_KEY", property = "roleKey"),
    @Result(column = "ROLE_ID", property = "roleId"),
    @Result(column = "NAME", property = "name"),
    @Result(column = "DESCRIPTION", property = "description")
  })
  List<RoleEntity> findByMember(
      @Param("memberId") String memberId, @Param("memberType") String memberType);

  @Insert(
      "INSERT INTO AUTH_ROLE ("
          + "ROLE_KEY, ROLE_ID, NAME, DESCRIPTION"
          + ") VALUES ("
          + "#{roleKey}, #{roleId}, #{name}, #{description})")
  void insert(RoleEntity entity);

  @Update(
      "UPDATE AUTH_ROLE SET "
          + "NAME = #{name}, "
          + "DESCRIPTION = #{description} "
          + "WHERE ROLE_ID = #{roleId}")
  int update(RoleEntity entity);

  @Delete("DELETE FROM AUTH_ROLE WHERE ROLE_ID = #{roleId}")
  void deleteById(@Param("roleId") String roleId);

  @Insert(
      "INSERT INTO AUTH_ROLE_MEMBER ("
          + "ROLE_ID, MEMBER_ID, MEMBER_TYPE"
          + ") VALUES ("
          + "#{entityId}, #{memberId}, #{memberType})")
  void insertMember(MembershipEntity entity);

  @Delete(
      "DELETE FROM AUTH_ROLE_MEMBER " + "WHERE ROLE_ID = #{entityId} AND MEMBER_ID = #{memberId}")
  void deleteMember(MembershipEntity entity);
}
