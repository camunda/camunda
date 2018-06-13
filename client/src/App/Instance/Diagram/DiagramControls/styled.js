import styled from 'styled-components';

import {Colors} from 'modules/theme';

export const DiagramControls = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  right: 20px;
  bottom: 20px;
  z-index: 2;
`;

export const Box = styled.button`
  font-size: 20px;
  border: solid 1px ${Colors.uiDark06};
  background-color: ${Colors.uiDark05};
  padding: 6px 12px;
`;

export const ZoomReset = Box.extend`
  border-radius: 3px;
  margin-bottom: 10px;
`;

export const ZoomIn = Box.extend`
  border-top-left-radius: 3px;
  border-top-right-radius: 3px;
  color: #ffffff;
`;

export const ZoomOut = Box.extend`
  border-bottom-left-radius: 3px;
  border-bottom-right-radius: 3px;
  color: #ffffff;
`;
