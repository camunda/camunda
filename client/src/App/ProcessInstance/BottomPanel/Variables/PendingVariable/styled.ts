/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const DisplayText = styled.div`
  word-break: break-word;
  margin: 11px 0;
  max-height: 76px;
  overflow-y: auto;
  overflow-wrap: break-word;
  padding-left: 9px;
`;

const Container = styled.div`
  ${({theme}) => {
    return css`
      display: flex;
      align-items: center;
      width: 100%;
      min-width: 400px;
      color: ${theme.colors.text01};
    `;
  }}
`;

const Name = styled.div`
  ${styles.bodyShort01};
  font-weight: 500;
  height: 100%;
  padding: 4px 0 4px 19px;
  margin: 3px 0;
  display: block;
  text-overflow: ellipsis;
  overflow: hidden;
  width: 30%;
`;

const Value = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 70%;
  padding-right: 16px;
`;

const SpinnerContainer = styled.div`
  width: 10%;
  display: flex;
  justify-content: flex-end;
`;

export {DisplayText, Container, Name, Value, SpinnerContainer};
