/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import BottomPanel from './index';
import {DataManagerProvider} from 'modules/DataManager';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import PropTypes from 'prop-types';
import {EXPAND_STATE} from 'modules/constants';

const COPYRIGHT_REGEX = /^© Camunda Services GmbH \d{4}. All rights reserved./;

describe('BottomPanel', () => {
  const ChildNode = ({expandState, ...props}) => (
    <>
      {EXPAND_STATE.DEFAULT === expandState && (
        <div {...props}>default content</div>
      )}
      {EXPAND_STATE.EXPANDED === expandState && (
        <div {...props}>expanded content</div>
      )}
      {EXPAND_STATE.COLLAPSED === expandState && (
        <div {...props}>collapsed content</div>
      )}{' '}
    </>
  );

  ChildNode.propTypes = {
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
  };

  it('should render default component', () => {
    beforeAll(() => {
      createMockDataManager();
    });
    render(
      <DataManagerProvider>
        <BottomPanel expandState={EXPAND_STATE.DEFAULT}>
          <ChildNode />
        </BottomPanel>
      </DataManagerProvider>
    );
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    expect(screen.getByText('Show End Time')).toBeInTheDocument();
    expect(screen.getByText('default content')).toBeInTheDocument();
    expect(screen.getByText(COPYRIGHT_REGEX)).toBeInTheDocument();
  });

  it('should render expanded component', () => {
    beforeAll(() => {
      createMockDataManager();
    });
    render(
      <DataManagerProvider>
        <BottomPanel expandState={EXPAND_STATE.EXPANDED}>
          <ChildNode />
        </BottomPanel>
      </DataManagerProvider>
    );
    expect(screen.getByText('expanded content')).toBeInTheDocument();
  });

  it('should render collapsed component', () => {
    beforeAll(() => {
      createMockDataManager();
    });
    render(
      <DataManagerProvider>
        <BottomPanel expandState={EXPAND_STATE.COLLAPSED}>
          <ChildNode />
        </BottomPanel>
      </DataManagerProvider>
    );
    expect(screen.getByText('collapsed content')).toBeInTheDocument();
  });
});
