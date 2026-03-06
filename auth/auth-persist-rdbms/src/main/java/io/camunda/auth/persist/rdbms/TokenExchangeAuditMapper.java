/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** MyBatis mapper for the TOKEN_EXCHANGE_AUDIT table. */
@Mapper
public interface TokenExchangeAuditMapper {

  @Insert(
      "INSERT INTO TOKEN_EXCHANGE_AUDIT ("
          + "EXCHANGE_ID, SUBJECT_PRINCIPAL_ID, ACTOR_PRINCIPAL_ID, TARGET_AUDIENCE, "
          + "GRANTED_SCOPES, EXCHANGE_TIME, EXPIRY_TIME, EXCHANGE_STATUS, IDP_TYPE, TENANT_ID"
          + ") VALUES ("
          + "#{exchangeId}, #{subjectPrincipalId}, #{actorPrincipalId}, #{targetAudience}, "
          + "#{grantedScopes}, #{exchangeTime}, #{expiryTime}, #{exchangeStatus}, "
          + "#{idpType}, #{tenantId})")
  void insert(TokenExchangeAuditEntity entity);

  @Select("SELECT * FROM TOKEN_EXCHANGE_AUDIT WHERE EXCHANGE_ID = #{exchangeId}")
  @Results({
    @Result(column = "EXCHANGE_ID", property = "exchangeId"),
    @Result(column = "SUBJECT_PRINCIPAL_ID", property = "subjectPrincipalId"),
    @Result(column = "ACTOR_PRINCIPAL_ID", property = "actorPrincipalId"),
    @Result(column = "TARGET_AUDIENCE", property = "targetAudience"),
    @Result(column = "GRANTED_SCOPES", property = "grantedScopes"),
    @Result(column = "EXCHANGE_TIME", property = "exchangeTime"),
    @Result(column = "EXPIRY_TIME", property = "expiryTime"),
    @Result(column = "EXCHANGE_STATUS", property = "exchangeStatus"),
    @Result(column = "IDP_TYPE", property = "idpType"),
    @Result(column = "TENANT_ID", property = "tenantId")
  })
  TokenExchangeAuditEntity findByExchangeId(@Param("exchangeId") String exchangeId);

  @Select(
      "SELECT * FROM TOKEN_EXCHANGE_AUDIT "
          + "WHERE SUBJECT_PRINCIPAL_ID = #{subjectPrincipalId} "
          + "AND EXCHANGE_TIME >= #{from} "
          + "AND EXCHANGE_TIME <= #{to} "
          + "ORDER BY EXCHANGE_TIME DESC")
  @Results({
    @Result(column = "EXCHANGE_ID", property = "exchangeId"),
    @Result(column = "SUBJECT_PRINCIPAL_ID", property = "subjectPrincipalId"),
    @Result(column = "ACTOR_PRINCIPAL_ID", property = "actorPrincipalId"),
    @Result(column = "TARGET_AUDIENCE", property = "targetAudience"),
    @Result(column = "GRANTED_SCOPES", property = "grantedScopes"),
    @Result(column = "EXCHANGE_TIME", property = "exchangeTime"),
    @Result(column = "EXPIRY_TIME", property = "expiryTime"),
    @Result(column = "EXCHANGE_STATUS", property = "exchangeStatus"),
    @Result(column = "IDP_TYPE", property = "idpType"),
    @Result(column = "TENANT_ID", property = "tenantId")
  })
  List<TokenExchangeAuditEntity> findBySubjectPrincipalId(
      @Param("subjectPrincipalId") String subjectPrincipalId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
