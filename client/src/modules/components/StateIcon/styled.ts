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

const iconPosition = css`
  position: relative;
  top: 3px;
  margin-right: 5px;
`;

const IncidentIcon = styled(StateIconIncident)`
  ${({theme}) => {
    return css`
      ${iconPosition};
      color: ${theme.colors.incidentsAndErrors};
    `;
  }}
`;

const ActiveIcon = styled(StateOk)`
  ${({theme}) => {
    return css`
      ${iconPosition};
      color: ${theme.colors.allIsWell};
    `;
  }}
`;

const CompletedIcon = styled(StateCompleted)`
  ${({theme}) => {
    const colors = theme.colors.modules.stateIcon;
    const opacity = theme.opacity.modules.stateIcon.completedIcon;

    return css`
      ${iconPosition};
      color: ${colors.color};
      opacity: ${opacity};
    `;
  }}
`;

const CanceledIcon = styled(Stop)`
  ${({theme}) => {
    const colors = theme.colors.modules.stateIcon;
    const opacity = theme.opacity.modules.stateIcon.canceledIcon;

    return css`
      ${iconPosition};
      color: ${colors.color};
      opacity: ${opacity};
    `;
  }}
`;

const AliasIcon = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.stateIcon;
    const opacity = theme.opacity.modules.stateIcon.aliasIcon;

    return css`
      ${iconPosition};
      height: 15px;
      width: 15px;
      border-radius: 50%;
      background: ${colors.color};
      opacity: ${opacity};
    `;
  }}
`;

export {IncidentIcon, ActiveIcon, CompletedIcon, CanceledIcon, AliasIcon};
