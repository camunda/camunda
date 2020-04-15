/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Input, Button} from 'components';
import {useTable, useSortBy, usePagination, useResizeColumns, useFlexLayout} from 'react-table';
import {flatten} from 'services';

import './Table.scss';
import {t} from 'translation';

const defaultPageSize = 20;

export default function Table({
  head,
  body,
  className,
  resultType,
  sortByLabel = false,
  updateSorting,
  sorting,
  disablePagination,
  disableReportScrolling,
  noData = t('common.noData'),
}) {
  const columns = React.useMemo(() => Table.formatColumns(head), [head]);
  const data = React.useMemo(() => Table.formatData(head, body), [head, body]);
  const initialSorting = React.useMemo(() => formatSorting(sorting, resultType, columns), [
    columns,
    resultType,
    sorting,
  ]);

  const {
    getTableProps,
    getTableBodyProps,
    headerGroups,
    prepareRow,
    nextPage,
    previousPage,
    canPreviousPage,
    canNextPage,
    page,
    pageCount,
    gotoPage,
    state: {pageIndex},
  } = useTable(
    {
      columns,
      data,
      manualSortBy: true,
      disableMultiSort: true,
      initialState: {
        pageIndex: 0,
        sortBy: initialSorting,
        pageSize: disablePagination ? Number.MAX_VALUE : defaultPageSize,
      },
    },
    useSortBy,
    usePagination,
    useFlexLayout,
    useResizeColumns
  );

  function getSortingProps(column) {
    const boxShadow =
      column.isSorted && sorting
        ? `inset 0 ${sorting.order === 'desc' ? '-' : ''}3px 0 0 rgba(0,0,0,.6)`
        : 'none';

    if (!updateSorting) {
      return {style: {boxShadow}};
    }
    const props = column.getSortByToggleProps();
    return {
      ...props,
      style: {
        cursor: 'pointer',
        boxShadow,
      },
      onClick: (evt) => {
        if (props.onClick) {
          props.onClick(evt);
          let sortColumn = column.id;
          if (resultType === 'map') {
            if (sortColumn === columns[0].accessor) {
              sortColumn = sortByLabel ? 'label' : 'key';
            } else {
              sortColumn = 'value';
            }
          }
          updateSorting(sortColumn, sorting?.order === 'asc' ? 'desc' : 'asc');
        }
      },
    };
  }

  return (
    <div
      className={classnames('Table', className, {
        'unscrollable-mode': disableReportScrolling,
      })}
    >
      <table {...getTableProps()} className="reactTable">
        <thead>
          {headerGroups.map((headerGroup) => (
            <tr {...headerGroup.getHeaderGroupProps()}>
              {headerGroup.headers.map((column) => {
                return (
                  <th
                    className={classnames('tableHeader', {placeholder: column.placeholderOf})}
                    {...column.getHeaderProps()}
                  >
                    <div className="cellContent" {...getSortingProps(column)}>
                      {column.render('Header')}
                    </div>
                    <div
                      {...column.getResizerProps()}
                      className={`resizer ${column.isResizing ? 'isResizing' : ''}`}
                    />
                  </th>
                );
              })}
            </tr>
          ))}
        </thead>
        <tbody {...getTableBodyProps()}>
          {page.map((row) => {
            prepareRow(row);
            return (
              <tr {...row.getRowProps(row.original.__props)}>
                {row.cells.map((cell) => {
                  return <td {...cell.getCellProps()}>{cell.render('Cell')}</td>;
                })}
              </tr>
            );
          })}
        </tbody>
      </table>
      {body.length === 0 && <div className="noData">{noData}</div>}
      {!disablePagination && pageCount !== 1 && (
        <div className="controls">
          <Button onClick={() => previousPage()} disabled={!canPreviousPage}>
            Previous
          </Button>
          <Input
            type="number"
            value={pageIndex + 1}
            onChange={(e) => {
              const page = e.target.value ? Number(e.target.value) - 1 : 0;
              gotoPage(page);
            }}
          />
          of {pageCount}
          <Button onClick={() => nextPage()} disabled={!canNextPage}>
            Next
          </Button>
        </div>
      )}
    </div>
  );
}

function formatSorting(sorting, resultType, columns) {
  if (!sorting) {
    return [];
  }
  const {by, order} = sorting;
  let id = by;
  if (resultType === 'map') {
    if (by === 'label' || by === 'key') {
      id = columns[0].accessor;
    } else if (by === 'value') {
      id = columns[1].accessor;
    }
  }
  return [{id, desc: order === 'desc'}];
}

Table.formatColumns = (head, ctx = '') => {
  return head.map((elem) => {
    if (typeof elem === 'string' || elem.id) {
      return {
        Header: elem.label || elem,
        accessor: convertHeaderNameToAccessor(ctx + (elem.id || elem)),
        minWidth: 100,
      };
    }
    return {
      Header: elem.label,
      columns: Table.formatColumns(elem.columns, ctx + elem.label),
    };
  });
};

Table.formatData = (head, body) => {
  const flatHead = head.reduce(
    flatten('', (entry) => entry.id || entry),
    []
  );
  return body.map((row) => {
    const newRow = {};

    const content = row.content || row;
    content.forEach((cell, columnIdx) => {
      newRow[convertHeaderNameToAccessor(flatHead[columnIdx])] = cell;
    });

    if (row.props) {
      newRow.__props = row.props;
    }

    return newRow;
  });
};

function convertHeaderNameToAccessor(name) {
  const joined = name
    .split(' ')
    .join('')
    .replace(t('report.variables.default'), t('report.groupBy.variable') + ':');

  return joined.charAt(0).toLowerCase() + joined.slice(1);
}
