/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Copyright as BaseCopyright} from 'modules/components/Copyright';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';
import StateIcon from 'modules/components/StateIcon';

const Container = styled.section`
  ${({theme}) => {
    const colors = theme.colors.decisionsList;
    return css`
      border: 1px solid ${colors.borderColor};
      background-color: ${colors.backgroundColor};
      display: flex;
      flex-direction: column;
      height: 100%;
    `;
  }}
`;

const DecisionContainer = styled.div`
  display: flex;
`;

const CircleBlock = styled(BaseCircle)`
  width: 14px;
  height: 14px;
  margin-right: 6px;
  margin-left: 14px;
`;

const DecisionBlock = styled(BaseBlock)`
  height: 14px;
  margin-left: 5px;
  width: 201px;
`;

const Copyright = styled(BaseCopyright)`
  text-align: right;
`;

const State = styled(StateIcon)`
  margin-right: 10px;
  &:first-child {
    margin-left: 0;
  }
`;
export {
  Container,
  DecisionContainer,
  DecisionBlock,
  CircleBlock,
  State,
  Copyright,
};
