/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useRef} from 'react';
import classnames from 'classnames';
import {Select, Icon, Button} from 'components';
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
  noHighlight,
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
    setPageSize,
    gotoPage,
    state: {pageSize, pageIndex},
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
  const firstRowIndex = pageIndex * pageSize;
  const maxLastRow = firstRowIndex + pageSize;
  const totalRows = body.length;
  const lastRowIndex = maxLastRow > totalRows ? totalRows : maxLastRow;

  function getSortingProps(column) {
    if (!updateSorting) {
      return {};
    }
    const props = column.getSortByToggleProps();
    return {
      ...props,
      style: {
        cursor: 'pointer',
      },
      onClick: (evt) => {
        if (props.onClick) {
          props.onClick(evt);
          let sortColumn = column.id;
          if (resultType === 'map') {
            if (sortColumn === columns[0].id) {
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

  const thead = useRef();
  const tr = useRef();
  useEffect(() => {
    if (tr.current && thead.current) {
      thead.current.style.width = tr.current.clientWidth + 'px';
    }
  }, []);

  return (
    <div className={classnames('Table', className, {highlight: !noHighlight})}>
      <table {...getTableProps()}>
        <thead ref={thead}>
          {headerGroups.map((headerGroup, i) => (
            <tr
              {...headerGroup.getHeaderGroupProps()}
              className={classnames({groupRow: i === 0 && headerGroups.length > 1})}
            >
              {headerGroup.headers.map((column) => (
                <th
                  className={classnames('tableHeader', {placeholder: column.placeholderOf})}
                  {...column.getHeaderProps()}
                >
                  <div className="cellContent" {...getSortingProps(column)}>
                    <span className="text">{column.render('Header')}</span>
                    {column.isSorted && sorting && (
                      <Icon type={sorting?.order === 'asc' ? 'up' : 'down'} />
                    )}
                  </div>
                  <div {...column.getResizerProps()} className="resizer" />
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody {...getTableBodyProps()}>
          {page.map((row) => {
            prepareRow(row);
            return (
              <tr {...row.getRowProps(row.original.__props)} ref={tr}>
                {row.cells.map((cell) => (
                  <td {...cell.getCellProps()}>{cell.render('Cell')}</td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
      {totalRows === 0 && <div className="noData">{noData}</div>}
      {!disablePagination && totalRows > defaultPageSize && (
        <div className="tableFooter">
          <div className="size">
            {t('report.table.rows')}
            <Select value={pageSize} onChange={(val) => setPageSize(Number(val))}>
              <Select.Option value={20}>20</Select.Option>
              <Select.Option value={100}>100</Select.Option>
              <Select.Option value={500}>500</Select.Option>
              <Select.Option value={1000}>1000</Select.Option>
            </Select>
          </div>
          <div
            className="info"
            dangerouslySetInnerHTML={{
              __html: t('report.table.info', {firstRowIndex, lastRowIndex, totalRows}),
            }}
          />
          <div className="controls">
            <Button className="first" icon onClick={() => gotoPage(0)} disabled={!canPreviousPage}>
              <Icon type="expand" />
            </Button>
            <Button
              className="previous"
              icon
              onClick={() => previousPage()}
              disabled={!canPreviousPage}
            >
              <Icon type="left" />
            </Button>
            <span>
              {t('report.table.page')} <b>{pageIndex + 1}</b> {t('report.table.of')}{' '}
              <b>{pageCount}</b>
            </span>
            <Button className="next" icon onClick={() => nextPage()} disabled={!canNextPage}>
              <Icon type="right" />
            </Button>
            <Button
              className="last"
              icon
              onClick={() => gotoPage(pageCount - 1)}
              disabled={!canNextPage}
            >
              <Icon type="collapse" />
            </Button>
          </div>
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
      id = columns[0].id;
    } else if (by === 'value') {
      id = columns[1].id;
    }
  }
  return [{id, desc: order === 'desc'}];
}

Table.formatColumns = (head, ctx = '') => {
  return head.map((elem) => {
    if (typeof elem === 'string' || elem.id) {
      const id = convertHeaderNameToAccessor(ctx + (elem.id || elem));
      return {
        Header: elem.label || elem,
        accessor: (d) => d[id],
        id,
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
