/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import BottomPanel from './index';
import {EXPAND_STATE} from 'modules/constants';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

type Props = {
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

const ChildNode: React.FC<Props> = ({expandState, ...props}) => (
  <>
    {EXPAND_STATE.DEFAULT === expandState && (
      <div {...props}>default content</div>
    )}
    {EXPAND_STATE.EXPANDED === expandState && (
      <div {...props}>expanded content</div>
    )}
    {EXPAND_STATE.COLLAPSED === expandState && (
      <div {...props}>collapsed content</div>
    )}
  </>
);

describe('BottomPanel', () => {
  it('should render default component', () => {
    render(
      <BottomPanel expandState={EXPAND_STATE.DEFAULT}>
        <ChildNode />
      </BottomPanel>,
      {wrapper: ThemeProvider}
    );
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    expect(screen.getByText('Show End Time')).toBeInTheDocument();
    expect(screen.getByText('default content')).toBeInTheDocument();
  });

  it('should render expanded component', () => {
    render(
      <BottomPanel expandState={EXPAND_STATE.EXPANDED}>
        <ChildNode />
      </BottomPanel>,
      {wrapper: ThemeProvider}
    );
    expect(screen.getByText('expanded content')).toBeInTheDocument();
  });

  it('should not render collapsed component', () => {
    render(
      <BottomPanel expandState={EXPAND_STATE.COLLAPSED}>
        <ChildNode />
      </BottomPanel>,
      {wrapper: ThemeProvider}
    );
    expect(screen.queryByText('collapsed content')).not.toBeInTheDocument();
  });
});
