/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {PANE_ID, EXPAND_STATE} from 'modules/constants';

import Pane from './index';

const SplitPaneHeader = () => <div>Header Content</div>;
const SplitPaneBody = () => <div>Body Content</div>;

const mockDefaultProps = {
  handleExpand: jest.fn(),
};

describe('Pane', () => {
  describe('top pane', () => {
    it('should not render expand buttons', () => {
      render(
        <Pane {...mockDefaultProps} expandState={EXPAND_STATE.COLLAPSED}>
          <SplitPaneHeader />
          <SplitPaneBody />
        </Pane>,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.queryByRole('button', {name: 'Expand Bottom'})
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: 'Expand Top'})
      ).not.toBeInTheDocument();
    });
  });

  describe('bottom pane', () => {
    it('should render CollapseButton with UP icon if pane is collapsed', () => {
      render(
        <Pane
          {...mockDefaultProps}
          expandState={EXPAND_STATE.COLLAPSED}
          paneId={'BOTTOM'}
        >
          <SplitPaneHeader />
          <SplitPaneBody />
        </Pane>,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.getByRole('button', {name: 'Expand Bottom'})
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: 'Expand Top'})
      ).not.toBeInTheDocument();
      expect(screen.getByTestId('icon-UP')).toBeInTheDocument();
    });

    it("'should render both CollapseButtons if pane is in default position", () => {
      render(
        <Pane
          {...mockDefaultProps}
          expandState={EXPAND_STATE.DEFAULT}
          paneId={'BOTTOM'}
        >
          <SplitPaneHeader />
          <SplitPaneBody />
        </Pane>,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.getByRole('button', {name: 'Expand Top'})
      ).toBeInTheDocument();
      expect(
        screen.getByRole('button', {name: 'Expand Bottom'})
      ).toBeInTheDocument();

      expect(screen.getByTestId('icon-DOWN')).toBeInTheDocument();
      expect(screen.getByTestId('icon-UP')).toBeInTheDocument();
    });

    it("should render CollapseButton with DOWN icon if pane is expanded'", () => {
      render(
        <Pane
          {...mockDefaultProps}
          expandState={EXPAND_STATE.EXPANDED}
          paneId={'BOTTOM'}
        >
          <SplitPaneHeader />
          <SplitPaneBody />
        </Pane>,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.getByRole('button', {name: 'Expand Top'})
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: 'Expand Bottom'})
      ).not.toBeInTheDocument();

      expect(screen.getByTestId('icon-DOWN')).toBeInTheDocument();
      expect(screen.queryByTestId('icon-UP')).not.toBeInTheDocument();
    });
  });

  describe('handleExpand', () => {
    const mockHandleExpand = jest.fn();

    afterEach(() => {
      mockHandleExpand.mockClear();
    });

    describe('handleTopExpand', () => {
      it('should call handleExpand with PANE_ID.TOP', () => {
        render(
          <Pane handleExpand={mockHandleExpand} paneId="BOTTOM">
            <SplitPaneHeader />
            <SplitPaneBody />
          </Pane>,
          {wrapper: ThemeProvider}
        );

        userEvent.click(screen.getByRole('button', {name: 'Expand Top'}));

        expect(mockHandleExpand).toHaveBeenCalledWith(PANE_ID.TOP);
      });
    });

    describe('handleBottomExpand', () => {
      it('should call handleExpand with PANE_ID.BOTTOM', () => {
        render(
          <Pane handleExpand={mockHandleExpand} paneId="BOTTOM">
            <SplitPaneHeader />
            <SplitPaneBody />
          </Pane>,
          {wrapper: ThemeProvider}
        );

        userEvent.click(screen.getByRole('button', {name: 'Expand Bottom'}));

        expect(mockHandleExpand).toHaveBeenCalledWith(PANE_ID.BOTTOM);
      });
    });
  });
});
