/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Input} from 'modules/components/Input';

const FiltersForm = styled.form`
  width: 328px;
  display: grid;
  grid-template-columns: 17px 1fr 17px;
  grid-column-gap: 3px;
  justify-content: center;
  padding: 0 2px;
`;

const Row = styled.div`
  grid-column-start: 2;
  width: 100%;
  height: fit-content;

  &:not(:last-child) {
    padding-bottom: 20px;
  }

  &:first-child {
    padding-top: 20px;
  }
`;

const VariableRow = styled(Row)`
  display: flex;
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
