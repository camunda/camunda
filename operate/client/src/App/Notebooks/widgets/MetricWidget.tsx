/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useQuery} from '@tanstack/react-query';
import {SkeletonText} from '@carbon/react';
import {requestWithThrow} from 'modules/request';
import type {WidgetConfig} from '../types';
import {
  MetricTile,
  MetricCaption,
  MetricValue,
  MetricSubvalue,
} from '../styled';

type Props = {
  config: WidgetConfig;
};

function getByPath(obj: unknown, path: string): unknown {
  return path
    .split('.')
    .reduce(
      (acc, key) =>
        acc != null && typeof acc === 'object'
          ? (acc as Record<string, unknown>)[key]
          : undefined,
      obj,
    );
}

const MetricWidget: React.FC<Props> = ({config}) => {
  const {title, query, field = 'page.totalItems', accent = 'info'} = config;

  const {data, status} = useQuery({
    queryKey: ['notebook-widget', config.id, query],
    queryFn: async () => {
      const {response, error} = await requestWithThrow<Record<string, unknown>>(
        {
          url: query.endpoint,
          method: query.method,
          body: query.body,
        },
      );
      if (error) {
        throw error;
      }
      return response;
    },
  });

  if (status === 'pending') {
    return (
      <MetricTile $accent={accent}>
        <MetricCaption>{title}</MetricCaption>
        <span role="status" data-testid="metric-skeleton">
          <SkeletonText heading width="80px" />
        </span>
      </MetricTile>
    );
  }

  if (status === 'error') {
    return (
      <MetricTile $accent={accent}>
        <MetricCaption>{title}</MetricCaption>
        <MetricValue>—</MetricValue>
        <MetricSubvalue>Could not load data</MetricSubvalue>
      </MetricTile>
    );
  }

  const rawValue = getByPath(data, field);
  const numericValue =
    typeof rawValue === 'number'
      ? rawValue.toLocaleString()
      : String(rawValue ?? '—');

  return (
    <MetricTile $accent={accent}>
      <MetricCaption>{title}</MetricCaption>
      <MetricValue>{numericValue}</MetricValue>
    </MetricTile>
  );
};

export {MetricWidget};
