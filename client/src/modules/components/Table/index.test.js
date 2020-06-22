/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Table from './index';

const {THead, TBody, TH, TR, TD} = Table;

describe('Table', () => {
  it('should render table', () => {
    // given
    const node = shallow(
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
      </Table>
    );

    expect(node).toMatchSnapshot();
  });
});
