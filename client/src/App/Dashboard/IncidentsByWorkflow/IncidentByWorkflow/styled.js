/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const Wrapper = themed(styled(withStrippedProps(['perUnit'])('div'))`
  display: flex;
  padding: 0;

  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06
  })};

  font-family: IBMPlexSans;
  font-size: ${({perUnit}) => (perUnit ? '13px' : '14px')};
  font-weight: ${({perUnit}) => (perUnit ? '400' : '600')}};
  line-height: 1.71;
`);

const redTextStyle = css`
  color: ${Colors.incidentsAndErrors};
`;

const greyTextStyle = css`
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.badge02
  })};

  opacity: 0.55;
`;

const greenTextStyle = css`
  color: ${Colors.allIsWell};
  opacity: 0.8;
`;

export const IncidentsCount = themed(styled.div`
  width: 96px;

  ${props => (props.hasIncidents ? redTextStyle : greyTextStyle)}
`);

export const ActiveCount = themed(styled.div`
  margin-left: auto;
  width: 139px;
  text-align: right;

  ${props => (props.hasActive ? greenTextStyle : greyTextStyle)}
`);

export const Label = themed(styled.div`
  opacity: ${themeStyle({
    dark: 0.9,
    light: 1
  })};
`);

const greenBarStyle = css`
  background: ${Colors.allIsWell};
  opacity: 0.8;
`;

const greyBarStyle = css`
  background: ${Colors.badge02};

  opacity: ${themeStyle({
    dark: 0.9,
    light: 0.4
  })};
`;

export const BarContainer = styled.div`
  position: relative;
  > div {
    height: ${props => (props.perUnit ? '3px' : '5px')};
  }
`;

export const Bar = themed(styled.div`
  ${props => (props.hasActive ? greenBarStyle : greyBarStyle)}
`);

export const IncidentsBar = styled.div`
  position: absolute;
  top: 0;

  background: ${Colors.incidentsAndErrors};
`;
