import React from 'react';
import PropTypes from 'prop-types';

import {ACTIVITY_STATE} from 'modules/constants';
import {themed} from 'modules/theme';

import * as Styled from './styled';

function getFlowNodeTypeIcon(type) {
  switch (type) {
    // events
    case 'bpmn:StartEvent':
      return Styled.StartEvent;
    case 'bpmn:EndEvent':
      return Styled.EndEvent;
    case 'bpmn:IntermediateCatchEvent':
      return Styled.StartEvent;
    case 'bpmn:BoundaryEvent':
      return Styled.StartEvent;
    // gateways
    case 'bpmn:ExclusiveGateway':
      return Styled.ExclusiveGateway;
    case 'bpmn:ParallelGateway':
      return Styled.ParallelGateway;
    // fallback the rest to Task
    default:
      return Styled.TaskDefault;
  }
}

function getStateIconByStateAndTheme({state, theme}) {
  switch (state) {
    case ACTIVITY_STATE.ACTIVE:
      return theme === 'dark' ? Styled.OkDark : Styled.OkLight;
    case ACTIVITY_STATE.INCIDENT:
      return theme === 'dark' ? Styled.IncidentDark : Styled.IncidentLight;
    case ACTIVITY_STATE.TERMINATED:
      return theme === 'dark' ? Styled.CanceledDark : Styled.CanceledLight;
    default:
      return theme === 'dark' ? Styled.CompletedDark : Styled.CompletedLight;
  }
}

function getStateIcon({state, theme, isSelected}) {
  if (isSelected) {
    switch (state) {
      case ACTIVITY_STATE.TERMINATED:
        return Styled.CanceledSelected;
      case ACTIVITY_STATE.COMPLETED:
        return Styled.CompletedSelected;
      default:
        return getStateIconByStateAndTheme({state, theme: 'light'});
    }
  }

  return getStateIconByStateAndTheme({state, theme});
}

function FlowNodeIcon({state, type, isSelected, ...props}) {
  // target flow node icon
  const TargetFlowNodeTypeIcon = getFlowNodeTypeIcon(type);

  // target state icon
  let TargetStateIcon = getStateIcon({state, theme: props.theme, isSelected});

  return (
    <Styled.IconContainer {...props}>
      <TargetFlowNodeTypeIcon />
      <TargetStateIcon />
    </Styled.IconContainer>
  );
}

FlowNodeIcon.propTypes = {
  state: PropTypes.oneOf(Object.values(ACTIVITY_STATE)).isRequired,
  type: PropTypes.string.isRequired,
  theme: PropTypes.string.isRequired,
  isSelected: PropTypes.bool
};

export default themed(FlowNodeIcon);
