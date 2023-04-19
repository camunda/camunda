/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {processInstancesStore} from 'modules/stores/processInstances';
import {ListFooter} from './index';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {useEffect} from 'react';

const DROPDOWN_REGEX = /^Apply Operation on \d+ Instance[s]?...$/;
const COPYRIGHT_REGEX = /^© Camunda Services GmbH \d{4}. All rights reserved./;

jest.mock('./CreateOperationDropdown', () => ({label}: any) => (
  <button>{label}</button>
));

const mockInstances: ProcessInstanceEntity[] = [
  {
    id: '2251799813685625',
    processId: '2251799813685623',
    processName: 'Without Incidents Process',
    processVersion: 1,
    startDate: '2020-11-19T08:14:05.406+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'withoutIncidentsProcess',
    hasActiveOperation: false,
    operations: [],
    sortValues: ['withoutIncidentsProcess', '2251799813685625'],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
  },
  {
    id: '2251799813685627',
    processId: '2251799813685623',
    processName: 'Without Incidents Process',
    processVersion: 1,
    startDate: '2020-11-19T08:14:05.490+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'withoutIncidentsProcess',
    hasActiveOperation: false,
    operations: [],
    sortValues: ['withoutIncidentsProcess', '2251799813685627'],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
  },
];

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return processInstancesSelectionStore.reset;
  }, []);

  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('ListFooter', () => {
  it('should show copyright, no dropdown', () => {
    processInstancesStore.setProcessInstances({
      filteredProcessInstancesCount: 11,
      processInstances: mockInstances,
    });

    render(<ListFooter />, {wrapper: Wrapper});

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();

    const dropdownButton = screen.queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();
  });

  it('should show Dropdown when there is selection', async () => {
    processInstancesStore.setProcessInstances({
      filteredProcessInstancesCount: 9,
      processInstances: mockInstances,
    });
    processInstancesSelectionStore.selectProcessInstance('1');
    processInstancesSelectionStore.selectProcessInstance('2');

    render(<ListFooter />, {wrapper: Wrapper});
    expect(
      await screen.findByText('Apply Operation on 2 Instances...')
    ).toBeInTheDocument();

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();
  });
});
