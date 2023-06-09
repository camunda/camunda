/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MouseEvent, ReactNode, UIEventHandler, useEffect, useMemo, useRef} from 'react';
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
  TableContainer,
  Table as CarbonTable,
  Pagination,
  TableHead,
  TableBody,
  TableRow,
  TableHeader,
  TableCell,
  DataTableSize,
  DataTableSortState,
  TableSelectAll,
  TableSelectRow,
  TableToolbar,
} from '@carbon/react';

import {t, getLanguage} from 'translation';
import {isReactElement} from 'services';
import {Select, LoadingIndicator, Tooltip, NoDataNotice} from 'components';

import {flatten, rewriteHeaderStyles} from './service';

import './Table.scss';

export interface Header<T extends object = object>
  extends ColumnInstance<T>,
    UseSortByColumnProps<T>,
    UseSortByOptions<T>,
    UseResizeColumnsColumnProps<T> {
  group: any;
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
  size?: DataTableSize;
  title?: string | JSX.Element[];
  toolbar?: ReactNode;
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
  size = 'lg',
  title,
  toolbar,
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
  const empty = !loading && (totalRows === 0 || head.length === 0);

  function getHeaderSortingDirection(header: Header): DataTableSortState {
    if (sorting?.order) {
      return sorting.order === 'desc' ? 'DESC' : 'ASC';
    }
    if (!header.isSorted) {
      return 'NONE';
    }
    if (header.isSortedDesc) {
      return 'DESC';
    }
    return 'ASC';
  }

  function getHeaderSortingProps(header: Header) {
    if (!updateSorting && !allowLocalSorting) {
      return {};
    }
    const props = header.getSortByToggleProps();
    return {
      ...props,
      isSortable: isSortable && header.canSort,
      isSortHeader: header.isSorted,
      sortDirection: getHeaderSortingDirection(header),
      onClick: (evt: MouseEvent) => {
        // dont do anything when clicker on column rearrangement or resizing
        if (
          evt?.target instanceof HTMLElement &&
          (evt.target.classList.contains('::after') || evt.target.classList.contains('resizer'))
        ) {
          return;
        }

        if (props.onClick) {
          props.onClick(evt);
          let sortColumn = header.id;
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

  const isSortable = headerGroups.some(({headers}) => headers.some((header) => header.canSort));

  if (isReactElement(toolbar) && toolbar.type !== TableToolbar) {
    throw new Error('Table `toolbar` should be a `TableToolbar` component');
  }

  return (
    <div
      className={classnames('Table', className, {
        highlight: !noHighlight,
        loading,
        noData: empty,
        error,
      })}
    >
      <DataTable
        isSortable={isSortable}
        locale={getLanguage()}
        headers={headers.map((header) => ({key: header.id, header: header.render('Header')!}))}
        rows={page}
        size={size}
        useZebraStyles
        render={({getTableContainerProps, getTableProps}) => (
          <TableContainer title={title} {...getTableContainerProps()}>
            {toolbar}
            <CarbonTable {...getReactTableProps()} {...getTableProps()}>
              <TableHead>
                {headerGroups.map((headerGroup, i) => (
                  <TableRow
                    {...headerGroup.getHeaderGroupProps()}
                    className={classnames({groupRow: i === 0 && headerGroups.length > 1})}
                  >
                    {headerGroup.headers.map((header: Header) => {
                      if (
                        typeof header.Header === 'object' &&
                        isReactElement(header.Header) &&
                        header.Header?.type === TableSelectAll
                      ) {
                        return header.render('Header');
                      }

                      const headerProps = {
                        ...getHeaderSortingProps(header),
                        ...header.getHeaderProps(),
                      };

                      return (
                        <TableHeader
                          className={classnames('tableHeader', {placeholder: header.placeholderOf})}
                          {...headerProps}
                          data-group={header.group}
                          scope="col"
                          title={undefined}
                          ref={rewriteHeaderStyles(headerProps.style)}
                        >
                          <Tooltip content={header.title} overflowOnly>
                            <span className="text">{header.render('Header')}</span>
                          </Tooltip>
                          <div {...header.getResizerProps()} className="resizer" />
                        </TableHeader>
                      );
                    })}
                  </TableRow>
                ))}
              </TableHead>
              <TableBody {...getTableBodyProps()} onScroll={onScroll}>
                {!error &&
                  page.map((row) => {
                    prepareRow(row);
                    return (
                      <TableRow {...row.getRowProps((row.original as any).__props)}>
                        {row.cells.map((cell) => {
                          if (cell.value?.type === TableSelectRow) {
                            return cell.render('Cell', {key: cell.value.props.id});
                          }

                          const props = cell.getCellProps();
                          return (
                            <TableCell
                              {...props}
                              className={classnames(props.className, {
                                noOverflow: cell.value?.type === Select,
                              })}
                            >
                              {cell.render('Cell')}
                            </TableCell>
                          );
                        })}
                      </TableRow>
                    );
                  })}
              </TableBody>
            </CarbonTable>
          </TableContainer>
        )}
      />
      {loading && <LoadingIndicator />}
      {error && <div>{error}</div>}
      {empty && <div className="noData">{noData}</div>}
      {!disablePagination && !empty && (totalRows > defaultPageSize || totalEntries) && (
        <Pagination
          onChange={({page, pageSize}) => {
            // react-table is counting index from 0, and the `page` here is counted form 1
            // for the `page` prop below, the situation is oposite
            gotoPage(page - 1);
            setPageSize(pageSize);
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
      return [{id: firstSortableColumn.id, desc: false}];
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
