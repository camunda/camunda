/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';

import {createMockDataManager} from 'modules/testHelpers/dataManager';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import {
  mockProps,
  mockPropsNoWorkflowSelected,
  mockPropsNoVersionSelected,
} from './index.setup';

import DiagramPanel from './index';
import {instancesDiagram} from 'modules/stores/instancesDiagram';
import PropTypes from 'prop-types';

const DiagramPanelWrapped = DiagramPanel.WrappedComponent;

jest.mock('modules/utils/bpmn');
jest.mock('modules/api/diagram', () => ({
  fetchWorkflowXML: jest.fn().mockImplementation(() => ''),
}));

const Wrapper = ({children}) => {
  return (
    <ThemeProvider>
      <CollapsablePanelProvider>{children} </CollapsablePanelProvider>
    </ThemeProvider>
  );
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('DiagramPanel', () => {
  let dataManager;

  beforeAll(() => {
    dataManager = createMockDataManager();
  });

  afterEach(() => {
    instancesDiagram.reset();
  });

  describe('DiagramPanel', () => {
    it('should render header', async () => {
      render(<DiagramPanelWrapped {...mockProps} {...{dataManager}} />, {
        wrapper: Wrapper,
      });
      await instancesDiagram.fetchWorkflowXml(1);

      expect(screen.getByText('Workflow Name')).toBeInTheDocument();
    });

    it('should show the loading indicator, when diagram is loading', async () => {
      render(<DiagramPanelWrapped {...mockProps} {...{dataManager}} />, {
        wrapper: Wrapper,
      });
      instancesDiagram.fetchWorkflowXml(1);

      expect(screen.getByTestId('spinner')).toBeInTheDocument();
      expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

      await waitForElementToBeRemoved(screen.getByTestId('spinner'));
    });

    it('should show an empty state message when no workflow is selected', async () => {
      render(
        <DiagramPanelWrapped
          {...mockPropsNoWorkflowSelected}
          {...{dataManager}}
        />,
        {
          wrapper: Wrapper,
        }
      );

      expect(
        screen.getByText('There is no Workflow selected.')
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          'To see a diagram, select a Workflow in the Filters panel.'
        )
      ).toBeInTheDocument();
      expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
    });

    it('should show a message when no workflow version is selected', async () => {
      render(
        <DiagramPanelWrapped
          {...mockPropsNoVersionSelected}
          {...{dataManager}}
        />,
        {
          wrapper: Wrapper,
        }
      );

      expect(
        screen.getByText(
          'There is more than one version selected for Workflow "Workflow Name".'
        )
      ).toBeInTheDocument();
      expect(
        screen.getByText('To see a diagram, select a single version.')
      ).toBeInTheDocument();

      expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
    });

    it('should render a diagram', async () => {
      render(<DiagramPanelWrapped {...mockProps} {...{dataManager}} />, {
        wrapper: Wrapper,
      });
      await instancesDiagram.fetchWorkflowXml(1);

      expect(screen.getByTestId('diagram')).toBeInTheDocument();
    });
  });
});
