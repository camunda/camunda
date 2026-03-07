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

/** MyBatis mapper for the AUTH_WEB_SESSION table. */
@Mapper
public interface WebSessionMapper {

  @Select("SELECT * FROM AUTH_WEB_SESSION WHERE SESSION_ID = #{sessionId}")
  @Results({
    @Result(column = "SESSION_ID", property = "sessionId"),
    @Result(column = "CREATION_TIME", property = "creationTime"),
    @Result(column = "LAST_ACCESSED_TIME", property = "lastAccessedTime"),
    @Result(column = "MAX_INACTIVE_INTERVAL_IN_SECONDS", property = "maxInactiveIntervalInSeconds"),
    @Result(
        column = "ATTRIBUTES",
        property = "attributes",
        typeHandler = MapByteArrayJsonTypeHandler.class)
  })
  WebSessionEntity findById(@Param("sessionId") String sessionId);

  @Select("SELECT * FROM AUTH_WEB_SESSION")
  @Results({
    @Result(column = "SESSION_ID", property = "sessionId"),
    @Result(column = "CREATION_TIME", property = "creationTime"),
    @Result(column = "LAST_ACCESSED_TIME", property = "lastAccessedTime"),
    @Result(column = "MAX_INACTIVE_INTERVAL_IN_SECONDS", property = "maxInactiveIntervalInSeconds"),
    @Result(
        column = "ATTRIBUTES",
        property = "attributes",
        typeHandler = MapByteArrayJsonTypeHandler.class)
  })
  List<WebSessionEntity> findAll();

  @Insert(
      "INSERT INTO AUTH_WEB_SESSION ("
          + "SESSION_ID, CREATION_TIME, LAST_ACCESSED_TIME, "
          + "MAX_INACTIVE_INTERVAL_IN_SECONDS, ATTRIBUTES"
          + ") VALUES ("
          + "#{sessionId}, #{creationTime}, #{lastAccessedTime}, "
          + "#{maxInactiveIntervalInSeconds}, "
          + "#{attributes, typeHandler=io.camunda.auth.persist.rdbms.MapByteArrayJsonTypeHandler})")
  void insert(WebSessionEntity entity);

  @Update(
      "UPDATE AUTH_WEB_SESSION SET "
          + "CREATION_TIME = #{creationTime}, "
          + "LAST_ACCESSED_TIME = #{lastAccessedTime}, "
          + "MAX_INACTIVE_INTERVAL_IN_SECONDS = #{maxInactiveIntervalInSeconds}, "
          + "ATTRIBUTES = #{attributes, typeHandler=io.camunda.auth.persist.rdbms.MapByteArrayJsonTypeHandler} "
          + "WHERE SESSION_ID = #{sessionId}")
  int update(WebSessionEntity entity);

  @Delete("DELETE FROM AUTH_WEB_SESSION WHERE SESSION_ID = #{sessionId}")
  void deleteById(@Param("sessionId") String sessionId);
}
