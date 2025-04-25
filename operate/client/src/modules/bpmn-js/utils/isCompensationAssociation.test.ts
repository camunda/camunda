/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isCompensationAssociation} from './isCompensationAssociation';

describe('isCompensationAssociation', () => {
  it('should return true for association with compensation target', () => {
    const association1: BusinessObject = {
      id: 'association1',
      name: 'Association 1',
      $type: 'bpmn:Association',
      targetRef: {
        name: 'Compensation Task',
        id: 'compensationTask',
        $type: 'bpmn:ServiceTask',
        isForCompensation: true,
      },
    };

    expect(isCompensationAssociation(association1)).toBe(true);
  });

  it('should return false for association without compensation target', () => {
    const association2: BusinessObject = {
      id: 'association2',
      name: 'Association 2',
      $type: 'bpmn:Association',
      targetRef: {
        name: 'Service Task',
        id: 'serviceTask',
        $type: 'bpmn:ServiceTask',
      },
    };

    expect(isCompensationAssociation(association2)).toBe(false);
  });

  it('should return false for non-associations', () => {
    const task: BusinessObject = {
      name: 'Service Task',
      id: 'serviceTask',
      $type: 'bpmn:ServiceTask',
    };

    expect(isCompensationAssociation(task)).toBe(false);
  });
});
