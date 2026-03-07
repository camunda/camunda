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

/** MyBatis mapper for the AUTH_GROUP and AUTH_GROUP_MEMBER tables. */
@Mapper
public interface GroupMapper {

  @Select("SELECT * FROM AUTH_GROUP WHERE GROUP_ID = #{groupId}")
  @Results({
    @Result(column = "GROUP_KEY", property = "groupKey"),
    @Result(column = "GROUP_ID", property = "groupId"),
    @Result(column = "NAME", property = "name"),
    @Result(column = "DESCRIPTION", property = "description")
  })
  GroupEntity findById(@Param("groupId") String groupId);

  @Select(
      "SELECT g.GROUP_KEY, g.GROUP_ID, g.NAME, g.DESCRIPTION "
          + "FROM AUTH_GROUP g "
          + "INNER JOIN AUTH_GROUP_MEMBER m ON g.GROUP_ID = m.GROUP_ID "
          + "WHERE m.MEMBER_ID = #{memberId} AND m.MEMBER_TYPE = #{memberType}")
  @Results({
    @Result(column = "GROUP_KEY", property = "groupKey"),
    @Result(column = "GROUP_ID", property = "groupId"),
    @Result(column = "NAME", property = "name"),
    @Result(column = "DESCRIPTION", property = "description")
  })
  List<GroupEntity> findByMember(
      @Param("memberId") String memberId, @Param("memberType") String memberType);

  @Insert(
      "INSERT INTO AUTH_GROUP ("
          + "GROUP_KEY, GROUP_ID, NAME, DESCRIPTION"
          + ") VALUES ("
          + "#{groupKey}, #{groupId}, #{name}, #{description})")
  void insert(GroupEntity entity);

  @Update(
      "UPDATE AUTH_GROUP SET "
          + "NAME = #{name}, "
          + "DESCRIPTION = #{description} "
          + "WHERE GROUP_ID = #{groupId}")
  int update(GroupEntity entity);

  @Delete("DELETE FROM AUTH_GROUP WHERE GROUP_ID = #{groupId}")
  void deleteById(@Param("groupId") String groupId);

  @Insert(
      "INSERT INTO AUTH_GROUP_MEMBER ("
          + "GROUP_ID, MEMBER_ID, MEMBER_TYPE"
          + ") VALUES ("
          + "#{entityId}, #{memberId}, #{memberType})")
  void insertMember(MembershipEntity entity);

  @Delete(
      "DELETE FROM AUTH_GROUP_MEMBER " + "WHERE GROUP_ID = #{entityId} AND MEMBER_ID = #{memberId}")
  void deleteMember(MembershipEntity entity);
}
