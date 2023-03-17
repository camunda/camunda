/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {SortableTable} from '../';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
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
      ariaLabel: `Data ${id}`,
      content: [
        {
          cellContent: id,
        },
        {
          cellContent: name,
        },
        {
          cellContent: version,
        },
      ],
      checkIsSelected: jest.fn(),
      onSelect: jest.fn(),
    };
  }) as React.ComponentProps<typeof SortableTable>['rows'],
  headerColumns: [
    {
      content: 'Column Header 1',
      sortKey: 'columnHeader1',
    },
    {
      content: 'Column Header 2',
      sortKey: 'columnHeader2',
    },
    {
      content: 'Column Header 3',
      sortKey: 'columnHeader3',
    },
  ],
  skeletonColumns: [
    {variant: 'block', width: '20px'},
    {variant: 'block', width: '20px'},
    {variant: 'block', width: '20px'},
  ],
  emptyMessage: 'List is empty',
} as React.ComponentProps<typeof SortableTable>;

const mockSelectableProps: {
  checkIsAllSelected: React.ComponentProps<
    typeof SortableTable
  >['checkIsAllSelected'];
  onSelectAll: React.ComponentProps<typeof SortableTable>['onSelectAll'];
  selectionType: React.ComponentProps<typeof SortableTable>['selectionType'];
} = {
  checkIsAllSelected: jest.fn(),
  onSelectAll: jest.fn(),
  selectionType: 'checkbox',
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

export {mockProps, mockSelectableProps, Wrapper};
