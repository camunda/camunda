/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Container = styled.div`
  display: flex;
  justify-content: space-between;
  flex-grow: 1;
  padding-right: 20px;
`;

const EditButtonsContainer = styled.div`
  padding-top: 8px;
`;

const Fields = styled.div`
  display: flex;
  width: 100%;
  margin-right: 2px;
`;

const Name = styled.div`
  min-width: 227px;
`;

const Value = styled.div`
  width: 100%;
`;

export {Container, Fields, Name, Value, EditButtonsContainer};
