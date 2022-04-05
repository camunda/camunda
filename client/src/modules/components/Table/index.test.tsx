/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import Table from './index';
const {THead, TBody, TH, TR, TD} = Table;

describe('Table', () => {
  it('should render table', () => {
    render(
      <Table>
        <THead>
          <TR>
            <TH>header 1</TH>
            <TH>header 2</TH>
          </TR>
        </THead>
        <TBody>
          <TR>
            <TD>cell 1 a</TD>
            <TD>cell 2 a</TD>
          </TR>
          <TR>
            <TD>cell 1 b</TD>
            <TD>cell 2 b</TD>
          </TR>
        </TBody>
      </Table>,
      {wrapper: ThemeProvider}
    );

    expect(screen.getByRole('columnheader', {name: 'header 1'}));
    expect(screen.getByRole('columnheader', {name: 'header 2'}));
    expect(screen.getByRole('cell', {name: 'cell 1 a'}));
    expect(screen.getByRole('cell', {name: 'cell 2 a'}));
    expect(screen.getByRole('cell', {name: 'cell 1 b'}));
    expect(screen.getByRole('cell', {name: 'cell 2 b'}));
  });
});
