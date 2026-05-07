/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {
  Pagination as ShadcnPagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from './pagination.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {PaginationProps as CarbonPaginationProps} from '@carbon/react';

export type PaginationProps = CarbonPaginationProps;

type CarbonChange = (data: {
  page: number;
  pageSize: number;
}) => void;

function buildPageList(current: number, total: number): Array<number | 'ellipsis'> {
  if (total <= 7) {
    return Array.from({length: total}, (_, i) => i + 1);
  }
  const pages: Array<number | 'ellipsis'> = [1];
  const start = Math.max(2, current - 1);
  const end = Math.min(total - 1, current + 1);
  if (start > 2) pages.push('ellipsis');
  for (let i = start; i <= end; i += 1) pages.push(i);
  if (end < total - 1) pages.push('ellipsis');
  pages.push(total);
  return pages;
}

function Pagination(props: PaginationProps) {
  const {
    page = 1,
    pageSize = 10,
    totalItems = 0,
    onChange,
    className,
    pageSizes,
    pageSizeInputDisabled,
    itemsPerPageText,
    itemRangeText,
    pageRangeText,
    pageNumberText,
    forwardText,
    backwardText,
    pageInputDisabled,
    disabled,
    isLastPage,
    pagesUnknown,
    size,
    ...rest
  } = props as PaginationProps & {
    page?: number;
    pageSize?: number;
    totalItems?: number;
    onChange?: CarbonChange;
    className?: string;
    pageSizes?: unknown;
    pageSizeInputDisabled?: boolean;
    itemsPerPageText?: React.ReactNode;
    itemRangeText?: unknown;
    pageRangeText?: unknown;
    pageNumberText?: string;
    forwardText?: string;
    backwardText?: string;
    pageInputDisabled?: boolean;
    disabled?: boolean;
    isLastPage?: boolean;
    pagesUnknown?: boolean;
    size?: string;
  };

  warnDroppedProps('Pagination', {
    pageSizes,
    pageSizeInputDisabled,
    itemsPerPageText,
    itemRangeText,
    pageRangeText,
    pageNumberText,
    forwardText,
    backwardText,
    pageInputDisabled,
    disabled,
    isLastPage,
    pagesUnknown,
    size,
  });

  const totalPages = Math.max(1, Math.ceil(totalItems / Math.max(1, pageSize)));
  const goTo = (next: number) => {
    if (!onChange) return;
    const clamped = Math.min(Math.max(1, next), totalPages);
    onChange({page: clamped, pageSize});
  };

  const pages = buildPageList(page, totalPages);

  return (
    <ShadcnPagination
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnPagination>)}
    >
      <PaginationContent>
        <PaginationItem>
          <PaginationPrevious
            href="#"
            onClick={(event) => {
              event.preventDefault();
              goTo(page - 1);
            }}
          />
        </PaginationItem>
        {pages.map((entry, index) =>
          entry === 'ellipsis' ? (
            <PaginationItem key={`ellipsis-${index}`}>
              <PaginationEllipsis />
            </PaginationItem>
          ) : (
            <PaginationItem key={entry}>
              <PaginationLink
                href="#"
                isActive={entry === page}
                onClick={(event) => {
                  event.preventDefault();
                  goTo(entry);
                }}
              >
                {entry}
              </PaginationLink>
            </PaginationItem>
          ),
        )}
        <PaginationItem>
          <PaginationNext
            href="#"
            onClick={(event) => {
              event.preventDefault();
              goTo(page + 1);
            }}
          />
        </PaginationItem>
      </PaginationContent>
    </ShadcnPagination>
  );
}

export {Pagination};
