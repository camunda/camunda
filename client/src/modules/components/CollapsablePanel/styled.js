import styled from 'styled-components';

import BasicPanel from '../Panel';

export const Collapsable = styled.div`
  overflow: hidden;
  transition: width 0.2s ease-out;
  width: ${({isCollapsed, maxWidth}) =>
    isCollapsed ? '56px' : `${maxWidth}px`};
  height: 100%;
  position: relative;
`;

export const Panel = styled(BasicPanel)`
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width 100%;
  transition: opacity 0.2s ease-out;
  opacity: ${({isCollapsed}) => (isCollapsed ? '0' : '1')};
`;
