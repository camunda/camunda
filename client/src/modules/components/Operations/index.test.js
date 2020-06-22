/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {STATE, OPERATION_STATE} from 'modules/constants';
import Operations from './index';
import {render, screen, fireEvent} from '@testing-library/react';

describe('Operations', () => {
  describe('Operation Buttons', () => {
    it('should render retry and cancel button if instance is running and has an incident', () => {
      render(
        <Operations.WrappedComponent
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
        />
      );

      expect(
        screen.getByTitle(`Retry Instance instance_1`)
      ).toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
    });
    it('should render only cancel button if instance is running and does not have an incident', () => {
      render(
        <Operations.WrappedComponent
          instance={{id: 'instance_1', state: STATE.ACTIVE, operations: []}}
        />
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
    });
    it('should not render retry and cancel buttons if instance is completed', () => {
      render(
        <Operations.WrappedComponent
          instance={{id: 'instance_1', state: STATE.COMPLETED, operations: []}}
        />
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
    });
    it('should not render retry and cancel buttons if instance is canceled', () => {
      render(
        <Operations.WrappedComponent
          instance={{id: 'instance_1', state: STATE.COMPLETED, operations: []}}
        />
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
    });

    it('should display spinner after clicking retry button', () => {
      render(
        <Operations.WrappedComponent
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
          onButtonClick={jest.fn()}
          dataManager={createMockDataManager()}
        />
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
      fireEvent.click(screen.getByTitle(`Retry Instance instance_1`));
      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });

    it('should display spinner after clicking cancel button', () => {
      render(
        <Operations.WrappedComponent
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
          onButtonClick={jest.fn()}
          dataManager={createMockDataManager()}
        />
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
      fireEvent.click(screen.getByTitle(`Cancel Instance instance_1`));
      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });
  });
  describe('Spinner', () => {
    it('should not display spinner', () => {
      render(
        <Operations.WrappedComponent
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
        />
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
    });
    it('should display spinner if it is forced', () => {
      render(
        <Operations.WrappedComponent
          instance={{id: 'instance_1', state: STATE.INCIDENT, operations: []}}
          forceSpinner={true}
        />
      );

      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });

    it("should display spinner if incident's latest operation is scheduled, locked or sent", () => {
      const {rerender} = render(
        <Operations.WrappedComponent
          instance={{
            id: 'instance_1',
            state: STATE.INCIDENT,
            operations: [{type: 'Retry', state: OPERATION_STATE.SCHEDULED}],
          }}
        />
      );

      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

      rerender(
        <Operations.WrappedComponent
          instance={{
            id: 'instance_1',
            state: STATE.INCIDENT,
            operations: [{type: 'Retry', state: OPERATION_STATE.LOCKED}],
          }}
        />
      );
      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

      rerender(
        <Operations.WrappedComponent
          instance={{
            id: 'instance_1',
            state: STATE.INCIDENT,
            operations: [{type: 'Retry', state: OPERATION_STATE.SENT}],
          }}
        />
      );
      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });
  });
});
