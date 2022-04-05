/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Button} from 'modules/components/Button';
import {ReactComponent as InfoIcon} from 'modules/icons/info.svg';
import {TD} from 'modules/components/Table';
import {LoadingOverlay} from 'modules/components/LoadingOverlay';
import {Spinner as BaseSpinner} from 'modules/components/LoadingOverlay/styled';

const Container = styled.div`
  margin-top: 13px;
`;

const ClaimButton = styled(Button)`
  margin-left: 22px;
  padding: 0 10px;
  display: flex;
  align-items: center;
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

const Spinner = styled(LoadingOverlay)`
  background-color: transparent;
  padding-right: 6px;
  width: unset;
  ${BaseSpinner} {
    width: 10px;
    height: 10px;
    border: 2px solid ${({theme}) => theme.colors.ui07};
    border-right-color: transparent;
  }
`;

export {Container, ClaimButton, Hint, Info, AssigneeTD, Assignee, Spinner};
