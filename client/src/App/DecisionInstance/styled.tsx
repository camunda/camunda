/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {HEADER_HEIGHT} from 'App/Header/styled';

const Container = styled.div`
  display: grid;
  height: calc(100vh - ${HEADER_HEIGHT}px);
  grid-template-rows: 50px 1fr 1fr;
`;

export {Container};
