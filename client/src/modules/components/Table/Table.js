/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useRef} from 'react';
import classnames from 'classnames';
import {useTable, useSortBy, usePagination, useResizeColumns, useFlexLayout} from 'react-table';

import {t} from 'translation';
import {Select, Icon, Button, LoadingIndicator, Tooltip, NoDataNotice} from 'components';

import {flatten} from './service';

import './Table.scss';

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
  noData = <NoDataNotice type="info" />,
  error,
  onScroll,
  fetchData = () => {},
  defaultPageSize = 20,
  defaultPage = 0,
  totalEntries,
  loading,
}) {
  const columnWidths = useRef({});
  const columns = React.useMemo(() => Table.formatColumns(head, '', columnWidths.current), [head]);
  const data = React.useMemo(() => Table.formatData(head, body), [head, body]);
  const initialSorting = React.useMemo(
    () => formatSorting(sorting, resultType, columns),
    [columns, resultType, sorting]
  );

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
      disableSortRemove: true,
      autoResetPage: false,
      initialState: {
        pageIndex: defaultPage,
        sortBy: initialSorting,
        pageSize: disablePagination ? Number.MAX_VALUE : defaultPageSize,
      },
      ...(totalEntries
        ? {manualPagination: true, pageCount: Math.ceil(totalEntries / defaultPageSize)}
        : {}),
    },
    useSortBy,
    usePagination,
    useFlexLayout,
    useResizeColumns
  );
  const firstRowIndex = pageIndex * pageSize;
  const maxLastRow = firstRowIndex + pageSize;
  const totalRows = totalEntries || body.length;
  const empty = !loading && (totalRows === 0 || head.length === 0);
  const lastRowIndex = maxLastRow > totalRows ? totalRows : maxLastRow;

  function getSortingProps(column) {
    if (!updateSorting) {
      return {};
    }
    const props = column.getSortByToggleProps();
    return {
      ...props,
      style: {
        cursor: column.disableSortBy ? 'default' : 'pointer',
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
  const tbody = useRef();
  useEffect(() => {
    if (window.ResizeObserver) {
      const ro = new ResizeObserver((entries) => {
        // We wrap it in requestAnimationFrame to avoid this error - ResizeObserver loop limit exceeded
        window.requestAnimationFrame(() => {
          if (!Array.isArray(entries) || !entries.length) {
            return;
          }
          if (tbody.current && thead.current) {
            thead.current.style.width = tbody.current.clientWidth + 'px';
          }
        });
      });
      if (tbody.current) {
        ro.observe(tbody.current);
      }
    }
  }, []);

  useEffect(() => {
    if (firstRowIndex >= totalRows) {
      gotoPage(pageCount - 1);
    }
  });

  const isInitialMount = useRef(true);
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
    } else {
      fetchData({pageIndex, pageSize});
    }
  }, [fetchData, pageIndex, pageSize]);

  useEffect(() => {
    headerGroups.forEach((group) =>
      group.headers.forEach(({id, width}) => (columnWidths.current[id] = width))
    );
  });

  return (
    <div className={classnames('Table', className, {highlight: !noHighlight, loading})}>
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
                  data-group={column.group}
                >
                  <div className="cellContent" {...getSortingProps(column)} title={undefined}>
                    <Tooltip content={column.title} overflowOnly>
                      <span className="text">{column.render('Header')}</span>
                    </Tooltip>
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
        <tbody {...getTableBodyProps()} onScroll={onScroll} ref={tbody}>
          {!error &&
            page.map((row) => {
              prepareRow(row);
              return (
                <tr {...row.getRowProps(row.original.__props)}>
                  {row.cells.map((cell) => {
                    const props = cell.getCellProps();
                    return (
                      <td
                        {...props}
                        className={classnames(props.className, {
                          noOverflow: cell.value?.type === Select,
                        })}
                      >
                        {cell.render('Cell')}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
        </tbody>
      </table>
      {loading && <LoadingIndicator />}
      {error && <div>{error}</div>}
      {empty && <div className="noData">{noData}</div>}
      {!disablePagination && !empty && (totalRows > defaultPageSize || totalEntries) && (
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

Table.formatColumns = (head, ctx = '', columnWidths = {}, group) => {
  return head.map((elem, idx) => {
    if (!elem.columns) {
      const id = convertHeaderNameToAccessor(ctx + (elem.id || elem));
      return {
        Header: elem.label || elem,
        title: elem.title,
        accessor: (d) => d[id],
        id,
        minWidth: elem.width || 100,
        disableSortBy: elem.sortable === false,
        width: columnWidths[id] || elem.width || 180,
        group,
      };
    }

    return {
      id: elem.id || elem.label,
      Header: elem.label,
      columns: Table.formatColumns(elem.columns, ctx + (elem.id || elem.label), columnWidths, idx),
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
