/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Table, Td, Th} from './styled';

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
    <Table>
      <thead>
        <tr>
          {headerColumns.map(({cellContent}, index) => {
            return <Th key={index}>{cellContent}</Th>;
          })}
        </tr>
      </thead>
      <tbody>
        {rows.map(({columns}, index) => {
          return (
            <tr key={index}>
              {columns.map(({cellContent}, index) => {
                return <Td key={index}>{cellContent}</Td>;
              })}
            </tr>
          );
        })}
      </tbody>
    </Table>
  );
};

export {DetailTable};
