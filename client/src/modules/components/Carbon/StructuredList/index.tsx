/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  StructuredListBody,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
} from '@carbon/react';
import {Container, StructuredListCell} from './styled';

type Column = {
  cellContent: React.ReactNode;
  width?: string;
};

type RowProps = {
  columns: Column[];
};

type Props = {
  label: string;
  headerSize?: 'sm' | 'md';
  headerColumns: Column[];
  rows: RowProps[];
  className?: string;
  dynamicRows?: React.ReactNode;
  verticalCellPadding?: string;
};

const StructuredRows: React.FC<Pick<Props, 'rows' | 'verticalCellPadding'>> = ({
  rows,
  verticalCellPadding,
}) => {
  return (
    <>
      {rows.map(({columns}, index) => (
        <StructuredListRow key={index}>
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
}) => {
  return (
    <Container className={className}>
      <StructuredListWrapper aria-label={label} isCondensed isFlush>
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
        <StructuredListBody>
          {dynamicRows}
          <StructuredRows
            rows={rows}
            verticalCellPadding={verticalCellPadding}
          />
        </StructuredListBody>
      </StructuredListWrapper>
    </Container>
  );
};

export {StructuredList, StructuredRows};
