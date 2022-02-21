/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {HEADER_HEIGHT} from 'App/Header/styled';

const DecisionInstanceContainer = styled.div`
  height: 100%;
  position: relative;
  display: grid;
  grid-template-rows: 56px 1fr 1fr;
`;

const Container = styled.main`
  height: calc(100vh - ${HEADER_HEIGHT}px);
`;

export {Container, DecisionInstanceContainer};
