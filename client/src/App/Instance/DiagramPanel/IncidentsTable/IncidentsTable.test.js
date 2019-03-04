/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Table from 'modules/components/Table';
import Button from 'modules/components/Button';
import ColumnHeader from '../../../Instances/ListView/List/ColumnHeader';
import Modal from 'modules/components/Modal';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createIncident} from 'modules/testUtils';
import {formatDate} from 'modules/utils/date';

import IncidentsTable from '../IncidentsTable';

const {TBody, TR, TD} = Table;

const shortError = 'No data found for query $.orderId.';
const longError =
  'Cannot compare values of different types: INTEGER and BOOLEAN';
const mockProps = {
  incidents: [
    createIncident({errorMessage: shortError, flowNodeName: 'Task A'}),
    createIncident({errorMessage: longError, flowNodeName: 'Task B'})
  ]
};

describe('IncidentsTable', () => {
  it('should render the right column headers', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable incidents={mockProps.incidents} />
      </ThemeProvider>
    );
    expect(node.find(ColumnHeader).length).toEqual(5);
    expect(
      node
        .find(ColumnHeader)
        .at(0)
        .text()
    ).toContain('Incident Type');
    expect(
      node
        .find(ColumnHeader)
        .at(1)
        .text()
    ).toContain('Flow Node');
    expect(
      node
        .find(ColumnHeader)
        .at(2)
        .text()
    ).toContain('Job Id');
    expect(
      node
        .find(ColumnHeader)
        .at(3)
        .text()
    ).toContain('Creation Time');
    expect(
      node
        .find(ColumnHeader)
        .at(4)
        .text()
    ).toContain('Error Message');
  });
  it('should render the right number of rows', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable incidents={mockProps.incidents} />
      </ThemeProvider>
    );
    expect(node.find(TBody).find(TR).length).toEqual(
      mockProps.incidents.length
    );
  });

  it('should render the right data in a row', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable incidents={mockProps.incidents} />
      </ThemeProvider>
    );
    expect(
      node
        .find(TBody)
        .find(TR)
        .find(TD).length
    ).toEqual(5 * mockProps.incidents.length);

    const firstRowCells = node
      .find(TBody)
      .find(TR)
      .at(0)
      .find(TD);

    expect(firstRowCells.at(0).text()).toContain(
      mockProps.incidents[0].errorType
    );
    expect(firstRowCells.at(1).text()).toContain(
      mockProps.incidents[0].flowNodeName
    );
    expect(firstRowCells.at(2).text()).toContain(
      mockProps.incidents[0].jobId || '--'
    );
    expect(firstRowCells.at(3).text()).toContain(
      formatDate(mockProps.incidents[0].creationTime)
    );
    expect(firstRowCells.at(4).text()).toContain(
      mockProps.incidents[0].errorMessage
    );
  });

  it('should show a more button for long error messages', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable incidents={mockProps.incidents} />
      </ThemeProvider>
    );
    const firstRowCells = node
      .find(TBody)
      .find(TR)
      .at(0)
      .find(TD);
    const secondRowCells = node
      .find(TBody)
      .find(TR)
      .at(1)
      .find(TD);

    expect(firstRowCells.at(4).text()).toContain(
      mockProps.incidents[0].errorMessage
    );
    expect(firstRowCells.at(4).find(Button)).not.toExist();

    // handle long error messages
    expect(secondRowCells.at(4).text()).toContain(
      mockProps.incidents[1].errorMessage
    );
    expect(secondRowCells.at(4).find(Button)).toExist();
    expect(
      secondRowCells
        .at(4)
        .find(Button)
        .text()
    ).toEqual('More...');
  });

  it('should open an modal when clicking on the more button', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable incidents={mockProps.incidents} />
      </ThemeProvider>
    );

    const secondRowCells = node
      .find(TBody)
      .find(TR)
      .at(1)
      .find(TD);
    const moreButton = secondRowCells.at(4).find(Button);

    // when
    moreButton.simulate('click');
    node.update();
    // expect modal to appear
    const modalNode = node.find('Modal');
    expect(modalNode).toHaveLength(1);
    // expect modal header to have the right text
    expect(modalNode.find(Modal.Header).text()).toContain(
      `Flow Node ${mockProps.incidents[1].flowNodeName} Error`
    );
    // expect modal content to have the right error message
    expect(modalNode.find(Modal.Body).text()).toContain(
      mockProps.incidents[1].errorMessage
    );
  });
});
