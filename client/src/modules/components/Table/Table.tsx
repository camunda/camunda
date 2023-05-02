/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {MouseEvent, UIEventHandler, useEffect, useRef} from 'react';
import classnames from 'classnames';
import {
  useTable,
  useSortBy,
  usePagination,
  useResizeColumns,
  useFlexLayout,
  Column,
  UseSortByColumnProps,
  UseSortByOptions,
  TableInstance,
  UseTableInstanceProps,
  UsePaginationInstanceProps,
  UseRowSelectInstanceProps,
  UseTableOptions,
  UsePaginationState,
} from 'react-table';

import {t} from 'translation';
import {Select, Icon, Button, LoadingIndicator, Tooltip, NoDataNotice} from 'components';

import {flatten} from './service';

import './Table.scss';

export type TableInstanceWithHooks<T extends object> = TableInstance<T> &
  UseTableInstanceProps<T> &
  UsePaginationInstanceProps<T> & {
    state: UsePaginationState<T>;
  } & UseRowSelectInstanceProps<T>;

export type Head =
  | string
  | (Partial<Column> & {
      label?: string;
      title?: string;
      sortable?: boolean;
      type?: string;
      columns?: Head[];
    });

type Body = (string | JSX.Element)[] | {content?: unknown[]; props: unknown};

interface TableProps {
  head: Head[];
  body: Body[];
  className?: string;
  resultType?: string;
  sortByLabel?: boolean;
  updateSorting?: (columneName: string | undefined, sorting: 'asc' | 'desc') => void;
  sorting?: {by: string; order: string};
  disablePagination?: boolean;
  noHighlight?: boolean;
  noData?: JSX.Element;
  error?: boolean;
  onScroll?: UIEventHandler<HTMLElement>;
  fetchData?: ({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => void;
  defaultPageSize?: number;
  defaultPage?: number;
  totalEntries?: number;
  loading?: boolean;
  allowLocalSorting?: boolean;
}

export default function Table<T extends object>({
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
  allowLocalSorting = false,
}: TableProps) {
  const columnWidths = useRef<Record<string, string | number | undefined>>({});
  const columns = React.useMemo(() => Table.formatColumns(head, '', columnWidths.current), [head]);
  const data = React.useMemo(() => Table.formatData(head, body), [head, body]);
  const initialSorting = React.useMemo(
    () => formatSorting(sorting, resultType, columns, allowLocalSorting),
    [columns, resultType, sorting, allowLocalSorting]
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
      manualSortBy: !allowLocalSorting,
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
    } as UseTableOptions<T>,
    useSortBy,
    usePagination,
    useFlexLayout,
    useResizeColumns
  ) as TableInstanceWithHooks<T>;

  const firstRowIndex = pageIndex * pageSize;
  const maxLastRow = firstRowIndex + pageSize;
  const totalRows = totalEntries || body.length;
  const empty = !loading && (totalRows === 0 || head.length === 0);
  const lastRowIndex = maxLastRow > totalRows ? totalRows : maxLastRow;

  function getSortingProps(column: Column & UseSortByOptions<T> & UseSortByColumnProps<T>) {
    if (!updateSorting && !allowLocalSorting) {
      return {};
    }
    const props = column.getSortByToggleProps();
    return {
      ...props,
      style: {
        cursor: column.disableSortBy ? 'default' : 'pointer',
      },
      onClick: (evt: MouseEvent) => {
        if (props.onClick) {
          props.onClick(evt);
          let sortColumn = column.id;
          if (resultType === 'map') {
            if (sortColumn === columns[0]?.id) {
              sortColumn = sortByLabel ? 'label' : 'key';
            } else {
              sortColumn = 'value';
            }
          }
          updateSorting?.(sortColumn, sorting?.order === 'asc' ? 'desc' : 'asc');
        }
      },
    };
  }

  const thead = useRef<HTMLTableSectionElement>(null);
  const tbody = useRef<HTMLTableSectionElement>(null);
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

  const isSortedDesc = (column: UseSortByColumnProps<T>) =>
    sorting ? sorting?.order === 'desc' : column.isSortedDesc;

  return (
    <div className={classnames('Table', className, {highlight: !noHighlight, loading})}>
      <table {...getTableProps()}>
        <thead ref={thead}>
          {headerGroups.map((headerGroup, i) => (
            <tr
              {...headerGroup.getHeaderGroupProps()}
              className={classnames({groupRow: i === 0 && headerGroups.length > 1})}
            >
              {headerGroup.headers.map((column: any) => (
                <th
                  className={classnames('tableHeader', {placeholder: column.placeholderOf})}
                  {...column.getHeaderProps()}
                  data-group={column.group}
                >
                  <div className="cellContent" {...getSortingProps(column)} title={undefined}>
                    <Tooltip content={column.title} overflowOnly>
                      <span className="text">{column.render('Header')}</span>
                    </Tooltip>
                    {column.isSorted && <Icon type={isSortedDesc(column) ? 'down' : 'up'} />}
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
                <tr {...row.getRowProps((row.original as any).__props)}>
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
          <div className="info">
            {t('report.table.info', {firstRowIndex, lastRowIndex, totalRows})}
          </div>
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

function formatSorting<T extends object>(
  sorting: {by: string; order: string} | undefined,
  resultType: string | undefined,
  columns: (Column & Partial<UseSortByOptions<T> & UseSortByColumnProps<T>>)[],
  allowLocalSorting: boolean
): {id?: string; desc?: boolean; order?: string}[] {
  if (allowLocalSorting) {
    const firstSortableColumn = columns.find((column) => !column.disableSortBy);
    if (firstSortableColumn) {
      return [{id: firstSortableColumn.id, order: 'desc'}];
    }
    return [];
  }

  if (!sorting) {
    return [];
  }
  const {by, order} = sorting;
  let id = by;
  if (resultType === 'map') {
    if (by === 'label' || by === 'key') {
      id = columns[0]?.id!;
    } else if (by === 'value') {
      id = columns[1]?.id!;
    }
  }
  return [{id, desc: order === 'desc'}];
}

Table.formatColumns = <T extends object = object>(
  head: Head[],
  ctx: string = '',
  columnWidths: Record<string, string | number | undefined> = {},
  group?: unknown
): (Column & Partial<UseSortByOptions<T> & UseSortByColumnProps<T>>)[] => {
  return head.map((elem, idx) => {
    if (typeof elem === 'string' || !elem.columns) {
      const id = convertHeaderNameToAccessor(ctx + (typeof elem === 'string' ? elem : elem.id));
      if (typeof elem === 'string') {
        return {
          Header: elem,
          title: undefined,
          accessor: (d: Record<string, unknown>) => d[id],
          id,
          minWidth: 100,
          disableSortBy: false,
          width: columnWidths[id] || 180,
          group,
        };
      }
      return {
        Header: elem.label,
        title: elem.title,
        accessor: (d: Record<string, unknown>) => d[id],
        id,
        minWidth: +(elem.width || 100),
        disableSortBy: elem.sortable === false,
        width: columnWidths[id] || elem.width || 180,
        group,
      };
    }

    return {
      id: elem.id || elem.label || '',
      Header: elem.label,
      columns: Table.formatColumns(elem.columns, ctx + (elem.id || elem.label), columnWidths, idx),
    };
  });
};

Table.formatData = (head: Head[], body: Body[]) => {
  const flatHead = head.reduce(
    flatten('', (entry) => (typeof entry === 'object' ? entry?.id : entry)),
    []
  );
  return body.map((row) => {
    const newRow: Record<string, unknown> & {__props?: unknown} = {};

    const content: unknown[] = Array.isArray(row) ? row : row.content || [];
    content.forEach((cell, columnIdx) => {
      newRow[convertHeaderNameToAccessor(flatHead[columnIdx] || '')] = cell;
    });

    if ('props' in row) {
      newRow.__props = row.props;
    }

    return newRow;
  });
};

function convertHeaderNameToAccessor(name: string) {
  const joined = name
    .split(' ')
    .join('')
    .replace(t('report.variables.default').toString(), t('report.groupBy.variable') + ':');

  return joined.charAt(0).toLowerCase() + joined.slice(1);
}
