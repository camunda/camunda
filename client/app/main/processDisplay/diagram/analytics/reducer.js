import {SELECT_PROCESS_DEFINITION} from 'main/processDisplay/controls/processDefinition/reducer';

export const ENTER_GATEWAY_ANALYSIS_MODE = 'ENTER_GATEWAY_ANALYSIS_MODE';
export const SET_ELEMENT = 'SET_ELEMENT';
export const GATEWAY_ANALYSIS_MODE = 'GATEWAY_ANALYSIS';

export const reducer = (state = {}, action) => {
  if (action.type === ENTER_GATEWAY_ANALYSIS_MODE) {
    return {
      ...state,
      mode: GATEWAY_ANALYSIS_MODE
    };
  } else if (action.type === SET_ELEMENT) {
    const newState = {
      ...state,
      [action.elementType]: action.id
    };

    if (!action.id) {
      // is anything is unset, we need to leave the gateway analysis mode
      newState.mode = null;
    }
    return newState;
  } else if (action.type === SELECT_PROCESS_DEFINITION) {
    return {
      ...state,
      mode: null,
      gateway: null,
      endEvent: null
    };
  }
  return state;
};

export function createEnterGatewayAnalysisModeAction() {
  return {
    type: ENTER_GATEWAY_ANALYSIS_MODE
  };
}

export function createSetElementAction(id, elementType) {
  return {
    type: SET_ELEMENT,
    id,
    elementType
  };
}
