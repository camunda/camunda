/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import {Table, TR, ColumnTH, TD, RowTH} from './Table';

export default {
  title: 'Components/Modules/Table',
};

const Default: React.FC = () => {
  return (
    <Table>
      <thead>
        <TR hasNoBorder>
          <ColumnTH>Field 0</ColumnTH>
          <ColumnTH>Field 1</ColumnTH>
        </TR>
      </thead>
      <tbody>
        <TR hasNoBorder>
          <TD>Value 0</TD>
          <TD>Value 1</TD>
        </TR>
      </tbody>
    </Table>
  );
};

const Horizontal: React.FC = () => {
  return (
    <Table>
      <TR>
        <RowTH>Field 0</RowTH>
        <TD>Value 0</TD>
      </TR>
      <TR hasNoBorder>
        <RowTH>Field 1</RowTH>
        <TD>Value 1</TD>
      </TR>
    </Table>
  );
};

export {Default, Horizontal};
