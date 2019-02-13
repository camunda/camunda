import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';

import StateIcon from 'modules/components/StateIcon';

export const NodeStateIcon = styled(
  withStrippedProps(['positionMultiplier'])(StateIcon)
)`
  top: 6px;
  left: ${({positionMultiplier}) =>
    positionMultiplier ? -positionMultiplier * 32 + 'px' : '5px'};
`;

const connectionDotStyles = css`
  height: 5px;
  width: 5px;
  border-radius: 50%;
  background: ${themeStyle({
    dark: '#65666D',
    light: Colors.uiLight05
  })};
`;

const connectionLineStyles = css`
  &:before {
    content: '';
    position: absolute;
    /* line ends 10px above the bottom of the element */
    height: calc(100% - 10px);
    width: 1px;
    left: -17px;
    background: ${themeStyle({
      dark: '#65666D',
      light: Colors.uiLight05
    })};
  }

  /* show a final dot at the end of each connection line */
  &:after {
    content: '';
    position: absolute;
    bottom: 10px;
    left: -19px;
    ${connectionDotStyles};
  }
`;

export const NodeDetails = themed(styled.div`
  display: flex;
  align-items: center;
  position: absolute;
  color: ${({theme, isSelected}) =>
    isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : 'rgba(69, 70, 78, 0.9)'};

  &:before {
    content: '';
    position: absolute;
    left: -51px;
    top: 13px;
    ${({showConnectionDot}) => showConnectionDot && connectionDotStyles};
  }
`);

export const Ul = themed(styled.ul`
  position: relative;
  ${({showConnectionLine}) => showConnectionLine && connectionLineStyles};
`);

export const Li = styled.li`
  margin-left: 32px;
`;
