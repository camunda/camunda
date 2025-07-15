/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SortableTable} from '../';
import {MemoryRouter} from 'react-router-dom';

const mockTableData = [
  {
    id: 'data-id-1',
    name: 'cell content 1',
    version: 1,
  },
  {
    id: 'data-id-2',
    name: 'cell content 2',
    version: '2',
  },
  {
    id: 'data-id-3',
    name: 'cell content 3',
    version: 1,
  },
  {
    id: 'data-id-4',
    name: 'cell content 4',
    version: 4,
  },
];

const mockProps = {
  rows: mockTableData.map(({id, name, version}) => {
    return {
      id,
      columnHeader1: id,
      columnHeader2: name,
      columnHeader3: version,
    };
  }) as React.ComponentProps<typeof SortableTable>['rows'],
  headerColumns: [
    {
      header: 'Column Header 1',
      key: 'columnHeader1',
    },
    {
      header: 'Column Header 2',
      key: 'columnHeader2',
    },
    {
      header: 'Column Header 3',
      key: 'columnHeader3',
    },
  ],
  emptyMessage: {message: 'List is empty'},
} as React.ComponentProps<typeof SortableTable>;

const mockSelectableProps: Pick<
  React.ComponentProps<typeof SortableTable>,
  | 'checkIsAllSelected'
  | 'onSelectAll'
  | 'onSelect'
  | 'checkIsRowSelected'
  | 'selectionType'
  | 'rowOperationError'
> = {
  checkIsAllSelected: vi.fn(),
  onSelectAll: vi.fn(),
  onSelect: vi.fn(),
  checkIsRowSelected: vi.fn(),
  rowOperationError: vi.fn(),
  selectionType: 'checkbox',
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return <MemoryRouter>{children}</MemoryRouter>;
};

export {mockProps, mockSelectableProps, Wrapper};
