/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Container = styled.section`
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-rows: 38px 1fr;
`;

const Tab = styled.button`
  all: unset;
  cursor: pointer;
  &:not(:last-child) {
    padding-right: 20px;
  }
`;

const Header = styled.header`
  padding: 10px 20px;
`;

const Content = styled.div`
  width: 100%;
  height: 100%;
  padding: 0 20px;
  display: flex;
`;

export {Container, Header, Content, Tab};
