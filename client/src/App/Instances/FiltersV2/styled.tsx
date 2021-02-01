/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Input} from 'modules/components/Input';

const FiltersForm = styled.form`
  width: 328px;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 10px;
`;

const Row = styled.div`
  display: flex;
  margin-right: 20px;
  margin-left: 20px;

  &:not(:last-child) {
    margin-bottom: 20px;
  }

  &:first-child {
    margin-top: 20px;
  }
`;

const VariableRow = styled(Row)`
  ${Input} {
    width: 50%;
  }

  ${Input}:focus {
    z-index: 1;
  }

  ${Input}:first-child:not(:focus) {
    border-right: none;
    border-top-right-radius: 0;
    border-bottom-right-radius: 0;
  }

  ${Input}:last-child:not(:focus) {
    border-top-left-radius: 0;
    border-bottom-left-radius: 0;
  }
`;

export {FiltersForm, Row, VariableRow};
