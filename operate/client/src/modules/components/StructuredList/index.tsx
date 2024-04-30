/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
} from '@carbon/react';
import {Container, StructuredListCell} from './styled';
import React, {useRef} from 'react';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';

type Column = {
  cellContent: React.ReactNode;
  width?: string;
};

type RowProps = {
  columns: Column[];
  dataTestId?: string;
  key: string;
};

type Props = {
  label: string;
  headerSize?: 'sm' | 'md';
  headerColumns: Column[];
  rows: RowProps[];
  className?: string;
  dynamicRows?: React.ReactNode;
  verticalCellPadding?: string;
  dataTestId?: string;
  isFlush?: boolean;
} & Pick<
  React.ComponentProps<typeof InfiniteScroller>,
  'onVerticalScrollStartReach' | 'onVerticalScrollEndReach'
>;

const StructuredRows: React.FC<Pick<Props, 'rows' | 'verticalCellPadding'>> = ({
  rows,
  verticalCellPadding,
}) => {
  return (
    <>
      {rows.map(({key, dataTestId, columns}) => (
        <StructuredListRow key={key} data-testid={dataTestId}>
          {columns.map(({cellContent, width}, index) => {
            return (
              <StructuredListCell
                key={index}
                $verticalCellPadding={verticalCellPadding}
                $width={width}
                onFocus={(e) => {
                  e.stopPropagation();
                }}
              >
                {cellContent}
              </StructuredListCell>
            );
          })}
        </StructuredListRow>
      ))}
    </>
  );
};

const StructuredList: React.FC<Props> = ({
  label,
  headerSize = 'md',
  headerColumns,
  rows,
  className,
  dynamicRows,
  verticalCellPadding,
  dataTestId,
  onVerticalScrollStartReach,
  onVerticalScrollEndReach,
  isFlush = true,
}) => {
  const scrollableContentRef = useRef<HTMLDivElement | null>(null);

  return (
    <Container
      className={className}
      data-testid={dataTestId}
      ref={scrollableContentRef}
    >
      <StructuredListWrapper aria-label={label} isCondensed isFlush={isFlush}>
        <StructuredListHead>
          <StructuredListRow head tabIndex={0}>
            {headerColumns.map(({cellContent, width}, index) => {
              return (
                <StructuredListCell
                  $width={width}
                  $size={headerSize}
                  head
                  key={index}
                >
                  {cellContent}
                </StructuredListCell>
              );
            })}
          </StructuredListRow>
        </StructuredListHead>
        <InfiniteScroller
          onVerticalScrollStartReach={onVerticalScrollStartReach}
          onVerticalScrollEndReach={onVerticalScrollEndReach}
          scrollableContainerRef={scrollableContentRef}
        >
          <div className="cds--structured-list-tbody" role="rowgroup">
            {dynamicRows}
            <StructuredRows
              rows={rows}
              verticalCellPadding={verticalCellPadding}
            />
          </div>
        </InfiniteScroller>
      </StructuredListWrapper>
    </Container>
  );
};

export {StructuredList, StructuredRows};
