/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as StateIconIncident} from 'modules/components/Icon/state-icon-incident.svg';
import {ReactComponent as StateOk} from 'modules/components/Icon/state-ok.svg';
import {ReactComponent as StateCompleted} from 'modules/components/Icon/state-completed.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';
import {Colors, themeStyle} from 'modules/theme';

const iconPosition = css`
  position: relative;
  top: 3px;
  margin-right: 5px;
`;

export const IncidentIcon = styled(StateIconIncident)`
  ${iconPosition};
  color: ${Colors.incidentsAndErrors};
`;

export const ActiveIcon = styled(StateOk)`
  ${iconPosition};
  color: ${Colors.allIsWell};
`;

export const CompletedIcon = styled(StateCompleted)`
  ${iconPosition};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06,
  })};
  opacity: ${themeStyle({
    dark: '0.46',
    light: '0.4',
  })};
`;

export const CanceledIcon = styled(Stop)`
  ${iconPosition};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06,
  })};
  opacity: ${themeStyle({
    dark: '0.81',
    light: '0.75',
  })};
`;

export const AliasIcon = styled.div`
  ${iconPosition};

  height: 15px;
  width: 15px;
  border-radius: 50%;

  background: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06,
  })};
  opacity: ${themeStyle({
    dark: '0.46',
    light: '0.4',
  })};
`;
