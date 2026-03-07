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

/** MyBatis mapper for the AUTH_MAPPING_RULE table. */
@Mapper
public interface MappingRuleMapper {

  @Select("SELECT * FROM AUTH_MAPPING_RULE WHERE MAPPING_RULE_ID = #{mappingRuleId}")
  @Results({
    @Result(column = "MAPPING_RULE_KEY", property = "mappingRuleKey"),
    @Result(column = "MAPPING_RULE_ID", property = "mappingRuleId"),
    @Result(column = "CLAIM_NAME", property = "claimName"),
    @Result(column = "CLAIM_VALUE", property = "claimValue"),
    @Result(column = "NAME", property = "name")
  })
  MappingRuleEntity findById(@Param("mappingRuleId") String mappingRuleId);

  @Select("SELECT * FROM AUTH_MAPPING_RULE")
  @Results({
    @Result(column = "MAPPING_RULE_KEY", property = "mappingRuleKey"),
    @Result(column = "MAPPING_RULE_ID", property = "mappingRuleId"),
    @Result(column = "CLAIM_NAME", property = "claimName"),
    @Result(column = "CLAIM_VALUE", property = "claimValue"),
    @Result(column = "NAME", property = "name")
  })
  List<MappingRuleEntity> findAll();

  @Insert(
      "INSERT INTO AUTH_MAPPING_RULE ("
          + "MAPPING_RULE_KEY, MAPPING_RULE_ID, CLAIM_NAME, CLAIM_VALUE, NAME"
          + ") VALUES ("
          + "#{mappingRuleKey}, #{mappingRuleId}, #{claimName}, #{claimValue}, #{name})")
  void insert(MappingRuleEntity entity);

  @Update(
      "UPDATE AUTH_MAPPING_RULE SET "
          + "CLAIM_NAME = #{claimName}, "
          + "CLAIM_VALUE = #{claimValue}, "
          + "NAME = #{name} "
          + "WHERE MAPPING_RULE_ID = #{mappingRuleId}")
  int update(MappingRuleEntity entity);

  @Delete("DELETE FROM AUTH_MAPPING_RULE WHERE MAPPING_RULE_ID = #{mappingRuleId}")
  void deleteById(@Param("mappingRuleId") String mappingRuleId);
}
