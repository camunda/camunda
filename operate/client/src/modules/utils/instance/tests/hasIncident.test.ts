/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {hasIncident} from '..';
import {mockIncidentInstance, mockActiveInstance} from './mocks';

describe('hasIncident', () => {
  it('should return true if an instance has an incident', () => {
    expect(hasIncident(mockIncidentInstance)).toBe(true);
  });

  it('should return false if an instance is active', () => {
    expect(hasIncident(mockActiveInstance)).toBe(false);
  });
});
