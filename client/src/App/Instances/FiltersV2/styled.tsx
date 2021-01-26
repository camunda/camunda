/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const FiltersForm = styled.form`
  width: 328px;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 10px;
`;

const Row = styled.div`
  display: flex;

  &:not(:last-child) {
    margin-bottom: 10px;
  }
`;

export {FiltersForm, Row};
