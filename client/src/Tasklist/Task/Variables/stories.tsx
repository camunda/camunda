/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import React from 'react';
import styled from 'styled-components';

import {
  EditTextarea,
  Cross,
  NameInputTD,
  ValueInputTD,
  IconTD,
  IconContainer,
  NameInput,
  IconButton,
  RowTH,
  Warning,
} from './styled';
import {Table, TD, TR} from 'modules/components/Table';

export default {
  title: 'Components/Tasklist/Right Panel',
};

const StyledTextarea = styled(EditTextarea)`
  width: 300px;
`;

const Input: React.FC = () => {
  return <NameInput name="variable" placeholder="Variable" />;
};

const InputError: React.FC = () => {
  return <NameInput name="variable" placeholder="Variable" aria-invalid />;
};

const Textarea: React.FC = () => {
  return <StyledTextarea name="value" placeholder="Value" />;
};

const TextareaError: React.FC = () => {
  return <StyledTextarea name="value" placeholder="Value" aria-invalid />;
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
          <ValueInputTD>
            <EditTextarea
              name="clientNo"
              id="clientNo"
              value={'"CNT-1211132-02"'}
            />
          </ValueInputTD>
          <IconTD />
        </TR>
        <TR>
          <RowTH>
            <label htmlFor="mwst">mwst</label>
          </RowTH>
          <ValueInputTD>
            <EditTextarea name="mwst" id="mwst" aria-invalid value={42.37} />
          </ValueInputTD>
          <IconTD>
            <Warning title="invalid" />
          </IconTD>
        </TR>
        <TR>
          <NameInputTD>
            <NameInput placeholder="Variable" name="variable" />
          </NameInputTD>
          <ValueInputTD>
            <EditTextarea placeholder="Value" name="value" />
          </ValueInputTD>
          <IconTD>
            <IconContainer>
              <IconButton type="button">
                <Cross />
              </IconButton>
            </IconContainer>
          </IconTD>
        </TR>
        <TR>
          <NameInputTD>
            <NameInput placeholder="Variable" aria-invalid />
          </NameInputTD>
          <ValueInputTD>
            <EditTextarea placeholder="Value" aria-invalid />
          </ValueInputTD>
          <IconTD>
            <IconContainer>
              <Warning title="invalid" />
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

export {Input, InputError, Textarea, TextareaError, VariablesTable};
