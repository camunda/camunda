/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getPriorityLabel} from './getPriorityLabel';

describe('getPriorityLabel', () => {
  it('should return the correct short priority label based on the priority value', () => {
    expect(getPriorityLabel(80).short).toEqual('Critical');
    expect(getPriorityLabel(60).short).toEqual('High');
    expect(getPriorityLabel(30).short).toEqual('Medium');
    expect(getPriorityLabel(20).short).toEqual('Low');
    expect(getPriorityLabel(25).short).toEqual('Low');
    expect(getPriorityLabel(50).short).toEqual('Medium');
    expect(getPriorityLabel(75).short).toEqual('High');
  });

  it('should return the correct long priority label based on the priority value', () => {
    expect(getPriorityLabel(80).long).toEqual('Priority: Critical');
    expect(getPriorityLabel(60).long).toEqual('Priority: High');
    expect(getPriorityLabel(30).long).toEqual('Priority: Medium');
    expect(getPriorityLabel(20).long).toEqual('Priority: Low');
    expect(getPriorityLabel(25).long).toEqual('Priority: Low');
    expect(getPriorityLabel(50).long).toEqual('Priority: Medium');
    expect(getPriorityLabel(75).long).toEqual('Priority: High');
  });

  it('should return the correct key based on the priority value', () => {
    expect(getPriorityLabel(80).key).toEqual('critical');
    expect(getPriorityLabel(60).key).toEqual('high');
    expect(getPriorityLabel(30).key).toEqual('medium');
    expect(getPriorityLabel(20).key).toEqual('low');
    expect(getPriorityLabel(25).key).toEqual('low');
    expect(getPriorityLabel(50).key).toEqual('medium');
    expect(getPriorityLabel(75).key).toEqual('high');
  });
});
