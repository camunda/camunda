/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
