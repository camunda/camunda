/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {ReactComponent as Check} from 'modules/components/Icon/check.svg';
import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

export const EmptyPanel = themed(styled.div`
  height: 100%;
  width: 100%;

  /* border-width: 1px; */
  /* border-style: solid; */
  /* border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })}; */
`);

const errorStyle = css`
  color: ${Colors.incidentsAndErrors};
  opacity: 0.9;
`;

const infoDarkStyle = css`
  color: #ffffff;
  opacity: 0.8;
`;

const infoLightStyle = css`
  color: ${Colors.uiLight08};
  opacity: 0.8;
`;

export const LabelContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 58%;
`;

export const Label = themed(styled.span`
  ${({type}) =>
    type === 'info'
      ? themeStyle({
          dark: infoDarkStyle,
          light: infoLightStyle
        })
      : errorStyle}

  font-family: IBMPlexSans;
  font-size: 16px;
  padding-top: 5px;
`);

export const CheckIcon = styled(Check)`
  width: 18px;
  height: 14px;
  fill: ${Colors.allIsWell};
  margin-right: 13px;
`;
export const WarningIcon = styled(Warning)`
  width: 20px;
  height: 18px;
  fill: ${Colors.incidentsAndErrors};
  margin-right: 15px;
`;
