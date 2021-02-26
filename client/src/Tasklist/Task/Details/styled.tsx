/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Button} from 'modules/components/Button';
import {ReactComponent as InfoIcon} from 'modules/icons/info.svg';
import {TD} from 'modules/components/Table';

const Container = styled.div`
  margin-top: 13px;
`;

const ClaimButton = styled(Button)`
  margin-left: 22px;
  padding: 0 18px;
`;

const AssigneeTD = styled(TD)`
  display: flex;
`;

const Assignee = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-width: 163px;
`;

const Hint = styled.div`
  display: flex;
  align-items: center;
  margin-left: 46px;
  color: ${({theme}) => theme.colors.hint.color};
  font-size: 13px;
  font-weight: 600;
  min-width: max-content;
`;

const Info = styled(InfoIcon)`
  margin-right: 8px;
`;

export {Container, ClaimButton, Hint, Info, AssigneeTD, Assignee};
