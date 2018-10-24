import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';

export const Diagram = styled.div`
  flex-grow: 1;
  position: relative;
`;

export const DiagramCanvas = themed(styled.div`
  position: absolute;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;

  .op-selectable:hover {
    cursor: pointer;
  }

  .op-selectable:hover .djs-outline {
    stroke-width: 3px;
    stroke: ${Colors.selections};
  }

  .op-selected .djs-outline {
    stroke-width: 3px;
    stroke: ${Colors.selections};
    fill: ${themeStyle({
      dark: 'rgba(58, 82, 125, 0.5)',
      light: 'rgba(189, 212, 253, 0.5)'
    })} !important;
  }
`);

export const span = css`
  padding: 0 11px 0 0px;
`;

export function getInlineStyle(state, theme) {
  let background = {
    active: {light: Colors.allIsWell, dark: Colors.allIsWell},
    incidents: {
      light: Colors.incidentsAndErrors,
      dark: Colors.incidentsAndErrors
    },
    completed: {
      light: Colors.badge01,
      dark: Colors.badge02
    },
    canceled: {
      light: Colors.badge02,
      dark: Colors.badge01
    }
  };

  let props = {
    display: 'flex',
    lineHeight: '24px',
    height: '24px',
    fontFamily: 'IBMPlexSans',
    fontSize: '13px',
    fontWeight: 'bold',
    color: '#ffffff',
    borderRadius: '12px',
    transform: 'translateX(-50%)'
  };

  props.backgroundColor = background[state][theme];

  if (state === 'completed' && theme === 'light') {
    props.color = Colors.uiLight06;
  }

  if (state === 'canceled' && theme === 'dark') {
    props.color = Colors.uiDark04;
  }

  // prettier-ignore
  return css`${props}`;
}
