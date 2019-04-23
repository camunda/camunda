/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import static org.camunda.optimize.service.util.ProcessVariableHelper.BOOLEAN_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.BOOLEAN_TYPE_LOWERCASE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.DATE_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.DATE_TYPE_LOWERCASE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.DOUBLE_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.DOUBLE_TYPE_LOWERCASE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.INTEGER_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.INTEGER_TYPE_LOWERCASE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.LONG_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.LONG_TYPE_LOWERCASE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.SHORT_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.SHORT_TYPE_LOWERCASE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.STRING_TYPE;
import static org.camunda.optimize.service.util.ProcessVariableHelper.STRING_TYPE_LOWERCASE;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StringVariableFilterDataDto.class, name = STRING_TYPE),
    @JsonSubTypes.Type(value = StringVariableFilterDataDto.class, name = STRING_TYPE_LOWERCASE),
    @JsonSubTypes.Type(value = ShortVariableFilterDataDto.class, name = SHORT_TYPE),
    @JsonSubTypes.Type(value = ShortVariableFilterDataDto.class, name = SHORT_TYPE_LOWERCASE),
    @JsonSubTypes.Type(value = LongVariableFilterDataDto.class, name = LONG_TYPE),
    @JsonSubTypes.Type(value = LongVariableFilterDataDto.class, name = LONG_TYPE_LOWERCASE),
    @JsonSubTypes.Type(value = DoubleVariableFilterDataDto.class, name = DOUBLE_TYPE),
    @JsonSubTypes.Type(value = DoubleVariableFilterDataDto.class, name = DOUBLE_TYPE_LOWERCASE),
    @JsonSubTypes.Type(value = IntegerVariableFilterDataDto.class, name = INTEGER_TYPE),
    @JsonSubTypes.Type(value = IntegerVariableFilterDataDto.class, name = INTEGER_TYPE_LOWERCASE),
    @JsonSubTypes.Type(value = BooleanVariableFilterDataDto.class, name = BOOLEAN_TYPE),
    @JsonSubTypes.Type(value = BooleanVariableFilterDataDto.class, name = BOOLEAN_TYPE_LOWERCASE),
    @JsonSubTypes.Type(value = DateVariableFilterDataDto.class, name = DATE_TYPE),
    @JsonSubTypes.Type(value = DateVariableFilterDataDto.class, name = DATE_TYPE_LOWERCASE),
})
@Data
public abstract class VariableFilterDataDto<DATA> implements FilterDataDto {
  @JsonProperty
  protected VariableType type;

  protected String name;
  protected DATA data;
}
