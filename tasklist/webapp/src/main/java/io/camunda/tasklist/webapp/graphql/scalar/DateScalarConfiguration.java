/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql.scalar;

import static io.camunda.tasklist.util.DateUtil.SIMPLE_DATE_FORMAT;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.GraphQLScalarType;
import java.text.ParseException;
import java.util.Date;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DateScalarConfiguration {

  @Bean
  public GraphQLScalarType javaUtilDateScalar() {
    return GraphQLScalarType.newScalar()
        .name("javaUtilDate")
        .description(
            "java.util.Date scalar compliant with RFC 3339 profile of the ISO 8601 standard")
        .coercing(
            new Coercing<Date, String>() {

              @Override
              public String serialize(Object dataFetcherResult) {
                if (dataFetcherResult instanceof Date) {
                  return SIMPLE_DATE_FORMAT.format((Date) dataFetcherResult);
                }
                throw new CoercingParseLiteralException("Cannot serialize " + dataFetcherResult);
              }

              @Override
              public Date parseValue(Object input) {
                try {
                  return SIMPLE_DATE_FORMAT.parse(String.valueOf(input));
                } catch (ParseException e) {
                  throw new CoercingParseLiteralException(
                      "Cannot parse " + input + " as DateTime", e);
                }
              }

              @Override
              public Date parseLiteral(Object input) {
                try {
                  if (!(input instanceof StringValue)) {
                    throw new CoercingParseLiteralException("String value expected for: " + input);
                  }
                  return SIMPLE_DATE_FORMAT.parse(((StringValue) input).getValue());
                } catch (ParseException e) {
                  throw new CoercingParseLiteralException(
                      "Cannot parse " + input + " as DateTime", e);
                }
              }
            })
        .build();
  }
}
