/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.converters;

import java.util.List;
import picocli.CommandLine.ITypeConverter;

public class ListConverter implements ITypeConverter<List<String>> {

  @Override
  public List<String> convert(final String s) throws Exception {
    final List<String> stringList = List.of(s.split(","));
    stringList.forEach(String::trim);
    return stringList;
  }
}
