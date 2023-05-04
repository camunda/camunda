/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
} from '@carbon/react';

type Column = {
  cellContent: React.ReactNode;
};

type RowProps = {
  columns: Column[];
};

type Props = {
  headerColumns: Column[];
  rows: RowProps[];
};

const DetailTable: React.FC<Props> = ({headerColumns, rows}) => {
  return (
    <StructuredListWrapper aria-label="DRD Details" isCondensed isFlush>
      <StructuredListHead>
        <StructuredListRow head tabIndex={0}>
          {headerColumns.map(({cellContent}, index) => {
            return (
              <StructuredListCell head key={index}>
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
  );
};

export {DetailTable};
