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
};

const StructuredList: React.FC<Props> = ({
  label,
  headerSize = 'md',
  headerColumns,
  rows,
  className,
}) => {
  return (
    <Container className={className}>
      <StructuredListWrapper aria-label={label} isCondensed isFlush>
        <StructuredListHead>
          <StructuredListRow head tabIndex={0}>
            {headerColumns.map(({cellContent}, index) => {
              return (
                <StructuredListCell $size={headerSize} head key={index}>
                  {cellContent}
                </StructuredListCell>
              );
            })}
          </StructuredListRow>
        </StructuredListHead>
        <StructuredListBody>
          {rows.map(({columns}, index) => {
            return (
              <StructuredListRow key={index}>
                {columns.map(({cellContent}, index) => {
                  return (
                    <StructuredListCell key={index}>
                      {cellContent}
                    </StructuredListCell>
                  );
                })}
              </StructuredListRow>
            );
          })}
        </StructuredListBody>
      </StructuredListWrapper>
    </Container>
  );
};

export {StructuredList};
