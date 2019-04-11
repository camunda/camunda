/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Table from 'modules/components/Table';
import {IncidentAction} from 'modules/components/Actions';
import Button from 'modules/components/Button';
import ColumnHeader from '../../../Instances/ListView/List/ColumnHeader';
import Modal from 'modules/components/Modal';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createIncident} from 'modules/testUtils';
import {formatDate} from 'modules/utils/date';

import IncidentsTable from '../IncidentsTable';
import {SORT_ORDER} from 'modules/constants';

const {TBody, TR, TD} = Table;

const id = 'flowNodeInstanceIdB';
const shortError = 'No data found for query $.orderId.';
const longError =
  'Cannot compare values of different types: INTEGER and BOOLEAN';
const mockProps = {
  incidents: [
    createIncident({
      errorType: 'Error A',
      errorMessage: shortError,
      flowNodeName: 'Task A',
      flowNodeInstanceId: 'flowNodeInstanceIdA'
    }),
    createIncident({
      errorType: 'Error B',
      errorMessage: longError,
      flowNodeName: 'Task B',
      flowNodeInstanceId: id
    })
  ],
  instanceId: '1',
  onIncidentOperation: jest.fn(),
  onIncidentSelection: jest.fn(),
  selectedFlowNodeInstanceIds: [id],
  sorting: {
    sortBy: 'errorType',
    sortOrder: SORT_ORDER.DESC
  },
  onSort: jest.fn()
};

describe('IncidentsTable', () => {
  beforeEach(() => {
    mockProps.onSort.mockClear();
    mockProps.onIncidentOperation.mockClear();
    mockProps.onIncidentSelection.mockClear();
  });
  it('should render the right column headers', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable {...mockProps} />
      </ThemeProvider>
    );
    expect(node.find(ColumnHeader).length).toEqual(6);
    expect(
      node
        .find(ColumnHeader)
        .at(0)
        .text()
    ).toContain('Incident Type');
    expect(
      node
        .find(ColumnHeader)
        .at(0)
        .props().sortKey
    ).toEqual('errorType');

    expect(
      node
        .find(ColumnHeader)
        .at(1)
        .text()
    ).toContain('Flow Node');
    expect(
      node
        .find(ColumnHeader)
        .at(1)
        .props().sortKey
    ).toEqual('flowNodeName');

    expect(
      node
        .find(ColumnHeader)
        .at(2)
        .text()
    ).toContain('Job Id');
    expect(
      node
        .find(ColumnHeader)
        .at(2)
        .props().sortKey
    ).toEqual('jobId');
    expect(
      node
        .find(ColumnHeader)
        .at(2)
        .props().disabled
    ).toBe(false);

    expect(
      node
        .find(ColumnHeader)
        .at(3)
        .text()
    ).toContain('Creation Time');
    expect(
      node
        .find(ColumnHeader)
        .at(3)
        .props().sortKey
    ).toEqual('creationTime');

    expect(
      node
        .find(ColumnHeader)
        .at(4)
        .text()
    ).toContain('Error Message');
    expect(
      node
        .find(ColumnHeader)
        .at(5)
        .text()
    ).toContain('Action');
  });

  it('should render the right number of rows', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable {...mockProps} />
      </ThemeProvider>
    );
    expect(node.find(TBody).find(TR).length).toEqual(
      mockProps.incidents.length
    );
  });

  it('should render the right data in a row', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable {...mockProps} />
      </ThemeProvider>
    );
    expect(
      node
        .find(TBody)
        .find(TR)
        .find(TD).length
    ).toEqual(6 * mockProps.incidents.length);

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
    expect(firstRowCells.at(5).find(IncidentAction)).toExist();
  });

  it('should show a more button for long error messages', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable {...mockProps} />
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
        <IncidentsTable {...mockProps} />
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
      `Flow Node "${mockProps.incidents[1].flowNodeName}" Error`
    );
    // expect modal content to have the right error message
    expect(modalNode.find(Modal.Body).text()).toContain(
      mockProps.incidents[1].errorMessage
    );
  });

  it('should call onIncidentOperation', async () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsTable {...mockProps} />
      </ThemeProvider>
    );
    const IncidentActionNode = node
      .find(TBody)
      .find(TR)
      .at(0)
      .find(IncidentAction);

    const onButtonClick = IncidentActionNode.props().onButtonClick;
    onButtonClick();

    node.update();

    expect(mockProps.onIncidentOperation).toHaveBeenCalled();
  });

  describe('Selection', () => {
    it('should call onIncidentSelection when clicking on a row', () => {
      const node = mount(
        <ThemeProvider>
          <IncidentsTable {...mockProps} />
        </ThemeProvider>
      );
      const RowNode = node
        .find(TBody)
        .find(TR)
        .at(0);

      RowNode.simulate('click');
      node.update();

      expect(mockProps.onIncidentSelection).toHaveBeenCalled();
      expect(mockProps.onIncidentSelection.mock.calls[0][0].id).toEqual(
        mockProps.incidents[0].flowNodeInstanceId
      );
      expect(mockProps.onIncidentSelection.mock.calls[0][0].activityId).toEqual(
        mockProps.incidents[0].flowNodeId
      );
    });

    it('should call onIncidentSelection when deselecting a row', () => {
      const node = mount(
        <ThemeProvider>
          <IncidentsTable {...mockProps} />
        </ThemeProvider>
      );

      const RowNode = node
        .find(TBody)
        .find(TR)
        .at(1);

      RowNode.simulate('click');
      node.update();

      node
        .find(TBody)
        .find(TR)
        .at(1)
        .simulate('click');
      node.update();

      expect(mockProps.onIncidentSelection).toHaveBeenCalled();
      expect(mockProps.onIncidentSelection.mock.calls[0][0].id).toEqual(
        mockProps.instanceId
      );
      expect(mockProps.onIncidentSelection.mock.calls[0][0].activityId).toEqual(
        null
      );
    });
    it('shoud mark the selected incidents row', () => {
      const node = mount(
        <ThemeProvider>
          <IncidentsTable {...mockProps} />
        </ThemeProvider>
      );

      expect(
        node
          .find(TBody)
          .find(TR)
          .at(0)
          .props().isSelected
      ).toBe(false);

      expect(
        node
          .find(TBody)
          .find(TR)
          .at(1)
          .props().isSelected
      ).toBe(true);
    });

    it('should handle multiple selections', () => {
      mockProps.selectedFlowNodeInstanceIds = [id, 'flowNodeInstanceIdA'];
      const node = mount(
        <ThemeProvider>
          <IncidentsTable {...mockProps} />
        </ThemeProvider>
      );

      expect(
        node
          .find(TBody)
          .find(TR)
          .at(0)
          .props().isSelected
      ).toBe(true);

      expect(
        node
          .find(TBody)
          .find(TR)
          .at(1)
          .props().isSelected
      ).toBe(true);

      mockProps.selectedFlowNodeInstanceIds = [id];
    });
  });

  describe('Sorting', () => {
    it('should call onSort when clicking on a column header', () => {
      const node = mount(
        <ThemeProvider>
          <IncidentsTable {...mockProps} />
        </ThemeProvider>
      );

      const randomColumn = Math.floor(Math.random() * 3);
      const ColumnHeaderNode = node.find(ColumnHeader).at(randomColumn);

      ColumnHeaderNode.simulate('click');

      expect(mockProps.onSort).toHaveBeenCalledWith(
        ColumnHeaderNode.props().sortKey
      );
    });
    it('should disable sorting for jobId column', () => {
      mockProps.incidents = [
        createIncident({
          errorType: 'Error A',
          errorMessage: shortError,
          flowNodeName: 'Task A',
          flowNodeInstanceId: 'flowNodeInstanceIdA',
          jobId: null
        }),
        createIncident({
          errorType: 'Error B',
          errorMessage: longError,
          flowNodeName: 'Task B',
          flowNodeInstanceId: id,
          jobId: null
        })
      ];
      const node = mount(
        <ThemeProvider>
          <IncidentsTable {...mockProps} />
        </ThemeProvider>
      );

      const ColumnHeaderNode = node.find(ColumnHeader).at(2);
      expect(ColumnHeaderNode.props().disabled).toBe(true);
    });
  });
});
