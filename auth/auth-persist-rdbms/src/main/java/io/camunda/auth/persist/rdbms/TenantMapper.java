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

/** MyBatis mapper for the AUTH_TENANT and AUTH_TENANT_MEMBER tables. */
@Mapper
public interface TenantMapper {

  @Select("SELECT * FROM AUTH_TENANT WHERE TENANT_ID = #{tenantId}")
  @Results({
    @Result(column = "TENANT_KEY", property = "tenantKey"),
    @Result(column = "TENANT_ID", property = "tenantId"),
    @Result(column = "NAME", property = "name"),
    @Result(column = "DESCRIPTION", property = "description")
  })
  TenantEntity findById(@Param("tenantId") String tenantId);

  @Select(
      "SELECT t.TENANT_KEY, t.TENANT_ID, t.NAME, t.DESCRIPTION "
          + "FROM AUTH_TENANT t "
          + "INNER JOIN AUTH_TENANT_MEMBER m ON t.TENANT_ID = m.TENANT_ID "
          + "WHERE m.MEMBER_ID = #{memberId} AND m.MEMBER_TYPE = #{memberType}")
  @Results({
    @Result(column = "TENANT_KEY", property = "tenantKey"),
    @Result(column = "TENANT_ID", property = "tenantId"),
    @Result(column = "NAME", property = "name"),
    @Result(column = "DESCRIPTION", property = "description")
  })
  List<TenantEntity> findByMember(
      @Param("memberId") String memberId, @Param("memberType") String memberType);

  @Insert(
      "INSERT INTO AUTH_TENANT ("
          + "TENANT_KEY, TENANT_ID, NAME, DESCRIPTION"
          + ") VALUES ("
          + "#{tenantKey}, #{tenantId}, #{name}, #{description})")
  void insert(TenantEntity entity);

  @Update(
      "UPDATE AUTH_TENANT SET "
          + "NAME = #{name}, "
          + "DESCRIPTION = #{description} "
          + "WHERE TENANT_ID = #{tenantId}")
  int update(TenantEntity entity);

  @Delete("DELETE FROM AUTH_TENANT WHERE TENANT_ID = #{tenantId}")
  void deleteById(@Param("tenantId") String tenantId);

  @Insert(
      "INSERT INTO AUTH_TENANT_MEMBER ("
          + "TENANT_ID, MEMBER_ID, MEMBER_TYPE"
          + ") VALUES ("
          + "#{entityId}, #{memberId}, #{memberType})")
  void insertMember(MembershipEntity entity);

  @Delete(
      "DELETE FROM AUTH_TENANT_MEMBER "
          + "WHERE TENANT_ID = #{entityId} AND MEMBER_ID = #{memberId}")
  void deleteMember(MembershipEntity entity);
}
