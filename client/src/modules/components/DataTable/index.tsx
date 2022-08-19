/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import Table from 'modules/components/Table';
import {Container, THead, TH, TR, TD} from './styled';

type Column = {
  id: string;
  cellContent: string | React.ReactNode;
  isBold?: boolean;
  width?: string;
};

type Row = {
  id: string;
  columns: Column[];
};

type Props = {
  headerColumns: Omit<Column, 'id'>[];
  rows: Row[];
};

const DataTable: React.FC<Props> = observer(({headerColumns, rows}) => {
  return (
    <Container>
      <Table>
        <THead>
          <TR>
            {headerColumns.map(({cellContent, isBold, width}, index) => (
              <TH key={index} $isBold={isBold} $width={width}>
                {cellContent}
              </TH>
            ))}
          </TR>
        </THead>
        <tbody>
          {rows.map(({id, columns}) => {
            return (
              <TR key={id}>
                {columns.map(({id, cellContent, isBold}) => (
                  <TD key={id} $isBold={isBold}>
                    {cellContent}
                  </TD>
                ))}
              </TR>
            );
          })}
        </tbody>
      </Table>
    </Container>
  );
});
export {DataTable};
