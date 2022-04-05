/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';

import {
  Cross,
  InputTD,
  IconTD,
  IconContainer,
  IconButton,
  RowTH,
} from './styled';
import {Table, TD, TR} from 'modules/components/Table';
import {TextField} from './TextField';
import {noop} from 'lodash';

export default {
  title: 'Components/Tasklist/Right Panel',
};

const VariablesTable: React.FC = () => {
  return (
    <Table>
      <tbody>
        <TR>
          <RowTH>amountToPay</RowTH>
          <TD>223</TD>
        </TR>
        <TR>
          <RowTH>
            <label htmlFor="clientNo">clientNo</label>
          </RowTH>
          <InputTD>
            <TextField
              name="clientNo"
              id="clientNo"
              value={'"CNT-1211132-02"'}
              onChange={noop}
              shouldDebounceError={false}
            />
          </InputTD>
          <IconTD />
        </TR>
        <TR>
          <RowTH>
            <label htmlFor="mwst">mwst</label>
          </RowTH>
          <InputTD>
            <TextField
              name="mwst"
              id="mwst"
              aria-invalid
              value="42.37"
              onChange={noop}
              shouldDebounceError={false}
            />
          </InputTD>
          <td />
        </TR>
        <TR>
          <InputTD>
            <TextField
              value=""
              placeholder="Variable"
              name="variable"
              onChange={noop}
              shouldDebounceError={false}
            />
          </InputTD>
          <InputTD>
            <TextField
              value=""
              placeholder="Value"
              name="value"
              onChange={noop}
              shouldDebounceError={false}
            />
          </InputTD>
          <IconTD>
            <IconContainer>
              <IconButton type="button">
                <Cross />
              </IconButton>
            </IconContainer>
          </IconTD>
        </TR>
        <TR>
          <InputTD>
            <TextField
              name=""
              value=""
              placeholder="Variable"
              aria-invalid
              onChange={noop}
              shouldDebounceError={false}
            />
          </InputTD>
          <InputTD>
            <TextField
              name=""
              value=""
              placeholder="Value"
              aria-invalid
              onChange={noop}
              shouldDebounceError={false}
            />
          </InputTD>
          <IconTD>
            <IconContainer>
              <IconButton type="button">
                <Cross />
              </IconButton>
            </IconContainer>
          </IconTD>
        </TR>
      </tbody>
    </Table>
  );
};

export {VariablesTable};
