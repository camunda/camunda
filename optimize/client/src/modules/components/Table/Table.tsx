/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode, UIEventHandler, useEffect, useMemo, useRef} from 'react';
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
  UsePaginationInstanceProps,
  UseRowSelectInstanceProps,
  UseTableOptions,
  UsePaginationState,
  UseResizeColumnsColumnProps,
  HeaderGroup as HG,
  ColumnInstance,
  TableState,
} from 'react-table';
import {
  DataTable,
  DataTableSkeleton,
  TableContainer,
  Table as CarbonTable,
  Pagination,
  TableHead,
  TableBody,
  TableRow,
  DataTableSize,
  TableToolbar,
} from '@carbon/react';

import {t, getLanguage} from 'translation';
import {isReactElement} from 'services';
import {NoDataNotice} from 'components';

import {convertHeaderNameToAccessor, flatten, formatSorting} from './service';
import TableHeader from './TableHeader';
import TableCell from './TableCell';

import './Table.scss';

export interface Header<T extends object = object>
  extends ColumnInstance<T>,
    UseSortByColumnProps<T>,
    UseSortByOptions<T>,
    UseResizeColumnsColumnProps<T> {
  group: unknown;
  title?: string;
}

interface HeaderGroup<T extends object = object> extends Omit<HG<T>, 'headers'> {
  headers: Header[];
}

export type TableInstanceWithHooks<T extends object = object> = Omit<
  TableInstance<T>,
  'headerGroups'
> &
  UsePaginationInstanceProps<T> &
  UseRowSelectInstanceProps<T> & {
    state: TableState<T> & UsePaginationState<T>;
    headerGroups: HeaderGroup[];
  };

export type Head =
  | string
  | (Partial<Column> & {
      label?: string | JSX.Element | JSX.Element[];
      title?: string;
      sortable?: boolean;
      type?: string;
      columns?: Head[];
    });

export type Body =
  | (string | JSX.Element | JSX.Element[])[]
  | {content?: (string | JSX.Element | JSX.Element[])[]; props: unknown};

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
  errorInPage?: ReactNode;
  onScroll?: UIEventHandler<HTMLElement>;
  fetchData?: ({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => void;
  defaultPageSize?: number;
  defaultPage?: number;
  totalEntries?: number;
  loading?: boolean;
  allowLocalSorting?: boolean;
  size?: DataTableSize;
  title?: string | JSX.Element[];
  toolbar?: ReactNode;
  useZebraStyles?: boolean;
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
  noData = <NoDataNotice />,
  errorInPage,
  onScroll,
  fetchData = () => {},
  defaultPageSize = 20,
  defaultPage = 0,
  totalEntries,
  loading,
  allowLocalSorting = false,
  size = 'lg',
  title,
  toolbar,
  useZebraStyles = true,
}: TableProps) {
  const columnWidths = useRef<Record<string, string | number | undefined>>({});
  const columns = useMemo(() => Table.formatColumns(head, '', columnWidths.current), [head]);
  const data = useMemo(() => Table.formatData(head, body), [head, body]);
  const initialSorting = useMemo(
    () => formatSorting(sorting, resultType, columns, allowLocalSorting),
    [columns, resultType, sorting, allowLocalSorting]
  );

  const {
    getTableProps: getReactTableProps,
    getTableBodyProps,
    headerGroups,
    prepareRow,
    page,
    headers,
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
    } as UseTableOptions<T> & UseSortByOptions<T>,
    useSortBy,
    usePagination,
    useFlexLayout,
    useResizeColumns
  ) as unknown as TableInstanceWithHooks<T>;

  const firstRowIndex = pageIndex * pageSize;
  const totalRows = totalEntries || body.length;

  useEffect(() => {
    if (firstRowIndex >= totalRows) {
      gotoPage(pageCount - 1);
    }
  });

  useEffect(() => {
    headerGroups.forEach((group) =>
      group.headers.forEach(({id, width}) => (columnWidths.current[id] = width))
    );
  });

  const isEmpty = !loading && (totalRows === 0 || head.length === 0);
  const isSortable = headerGroups.some(({headers}) => headers.some((header) => header.canSort));
  const showPagination =
    !disablePagination && !isEmpty && !loading && (totalRows > defaultPageSize || totalEntries);

  if (isReactElement(toolbar) && toolbar.type !== TableToolbar) {
    throw new Error('Table `toolbar` should be a `TableToolbar` component');
  }

  const MAX_LOADING_COLUMNS_COUNT = Math.max(Math.min(headers.length, 6), 3);
  const MAX_LOADING_ROWS_COUNT = Math.max(Math.min(body.length, 15), 3);

  return (
    <div
      className={classnames('Table', className, {
        noHighlight,
        loading,
        noData: isEmpty,
      })}
    >
      <DataTable
        isSortable={isSortable}
        locale={getLanguage()}
        headers={headers.map((header) => ({key: header.id, header: header.render('Header')!}))}
        rows={page}
        size={size}
        useZebraStyles={useZebraStyles}
        render={({getTableContainerProps, getTableProps}) => (
          <TableContainer title={title} {...getTableContainerProps()}>
            {toolbar}
            {!loading && (
              <CarbonTable {...getReactTableProps()} {...getTableProps()}>
                <TableHead>
                  {headerGroups.map((headerGroup, i) => {
                    const {key, ...headerGroupProps} = headerGroup.getHeaderGroupProps();
                    return (
                      <TableRow
                        key={key}
                        {...headerGroupProps}
                        className={classnames({groupRow: i === 0 && headerGroups.length > 1})}
                      >
                        {headerGroup.headers.map((header: Header) => (
                          <TableHeader
                            key={header.id}
                            header={header}
                            isSortable={isSortable}
                            allowLocalSorting={allowLocalSorting}
                            resultType={resultType}
                            sortByLabel={sortByLabel}
                            sorting={sorting}
                            updateSorting={updateSorting}
                            firstColumnId={columns[0]?.id}
                          />
                        ))}
                      </TableRow>
                    );
                  })}
                </TableHead>
                {!errorInPage && (
                  <TableBody {...getTableBodyProps()} onScroll={onScroll} tabIndex={0}>
                    {page.map((row) => {
                      prepareRow(row);
                      const {key, ...rowProps} = row.getRowProps((row.original as any).__props);
                      return (
                        <TableRow key={key} {...rowProps} tabIndex={0}>
                          {row.cells.map((cell) => (
                            <TableCell key={cell.column.id} cell={cell} />
                          ))}
                        </TableRow>
                      );
                    })}
                  </TableBody>
                )}
              </CarbonTable>
            )}
            {loading && (
              <DataTableSkeleton
                zebra={useZebraStyles}
                showToolbar={false}
                showHeader={false}
                headers={headers.map(getHeaderPlaceholder)}
                columnCount={MAX_LOADING_COLUMNS_COUNT}
                rowCount={MAX_LOADING_ROWS_COUNT}
                // TODO: Remove this when carbon fixes their type declarations
                compact={undefined}
                className={undefined}
              />
            )}
            {!loading && isEmpty && <div className="noData">{noData}</div>}
            {errorInPage && <div className="errorContainer">{errorInPage}</div>}
            {showPagination && (
              <Pagination
                aria-disabled={loading}
                onChange={({page, pageSize}) => {
                  // react-table is counting index from 0, and the `page` here is counted form 1
                  // for the `page` prop below, the situation is oposite
                  gotoPage(page - 1);
                  setPageSize(pageSize);
                  fetchData({pageIndex: page - 1, pageSize});
                }}
                totalItems={totalRows}
                page={pageIndex + 1}
                pageSize={pageSize}
                pageSizes={[20, 100, 500, 1000]}
                pageNumberText={t('report.table.page').toString()}
                itemsPerPageText={t('report.table.rows').toString()}
                itemRangeText={(min, max, total) =>
                  t('report.table.info', {
                    firstRowIndex: min,
                    lastRowIndex: max,
                    totalRows: total,
                  }).toString()
                }
                itemText={(min, max) => `${min} to ${max}`}
                pageRangeText={(current, total) =>
                  `${t('report.table.page')} ${current} ${t('report.table.of')} ${total}`
                }
                forwardText={t('report.table.nextPage').toString()}
                backwardText={t('report.table.previousPage').toString()}
              />
            )}
          </TableContainer>
        )}
      />
    </div>
  );
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
      id: elem.id || elem.label?.toString() || '',
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

function getHeaderPlaceholder<T extends object>(header: ColumnInstance<T>): {header: string} {
  const headerString = header.render('Header')!.toString();
  return {header: headerString === '[object Object]' ? '' : headerString};
}
