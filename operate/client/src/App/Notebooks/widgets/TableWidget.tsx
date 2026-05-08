/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useQuery} from '@tanstack/react-query';
import {DataTableSkeleton, Tile} from '@carbon/react';
import {requestWithThrow} from 'modules/request';
import type {WidgetConfig} from '../types';
import {WidgetTitle, WidgetSubtitle, WidgetTable, EmptyState} from '../styled';
import {columnLabel, renderCellValue} from './tableCells';

type Props = {
  config: WidgetConfig;
};

type ApiResponse = {
  items: Record<string, unknown>[];
  page?: {totalItems: number};
};

const TableWidget: React.FC<Props> = ({config}) => {
  const {title, subtitle, query, columns = []} = config;

  const {data, status} = useQuery({
    queryKey: ['notebook-widget', config.id, query],
    queryFn: async () => {
      const {response, error} = await requestWithThrow<ApiResponse>({
        url: query.endpoint,
        method: query.method,
        body: query.body,
      });
      if (error) {
        throw error;
      }
      return response;
    },
  });

  if (status === 'pending') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <DataTableSkeleton
          role="status"
          data-testid="table-skeleton"
          columnCount={columns.length || 3}
          rowCount={5}
        />
      </Tile>
    );
  }

  if (status === 'error') {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState>Could not load data.</EmptyState>
      </Tile>
    );
  }

  const items = data?.items ?? [];
  const totalItems = data?.page?.totalItems ?? items.length;

  if (items.length === 0) {
    return (
      <Tile>
        <WidgetTitle>{title}</WidgetTitle>
        {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
        <EmptyState>No data available.</EmptyState>
      </Tile>
    );
  }

  const MAX_ROWS = 15;
  const visibleItems = items.slice(0, MAX_ROWS);
  const truncated = items.length > MAX_ROWS;

  return (
    <Tile>
      <WidgetTitle>{title}</WidgetTitle>
      {subtitle && <WidgetSubtitle>{subtitle}</WidgetSubtitle>}
      <WidgetTable>
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col}>{columnLabel(col)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {visibleItems.map((item, rowIndex) => (
            <tr key={String(item['id'] ?? rowIndex)}>
              {columns.map((col) => (
                <td key={col}>{renderCellValue(col, item[col], item)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </WidgetTable>
      {(truncated || items.length < totalItems) && (
        <EmptyState style={{marginTop: 'var(--cds-spacing-04)'}}>
          Showing first {visibleItems.length} of {totalItems.toLocaleString()}{' '}
          rows.
        </EmptyState>
      )}
    </Tile>
  );
};

export {TableWidget};
