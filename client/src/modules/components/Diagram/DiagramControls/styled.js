import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

export const DiagramControls = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  right: 20px;
  bottom: 20px;
  z-index: 2;
`;

export const Box = themed(styled.button`
  font-size: 20px;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight03
    })};
  background-color: ${themeStyle({
    dark: Colors.uiDark05,
    light: Colors.uiLight05
  })};
  padding: 6px 12px;
`);

export const ZoomReset = styled(Box)`
  border-radius: 3px;
  margin-bottom: 10px;
`;

export const ZoomIn = themed(styled(Box)`
  border-top-left-radius: 3px;
  border-top-right-radius: 3px;
  color: ${themeStyle({
    dark: '#ffffff'
  })};
`);

export const ZoomOut = themed(styled(Box)`
  border-bottom-left-radius: 3px;
  border-bottom-right-radius: 3px;
  color: ${themeStyle({
    dark: '#ffffff'
  })};
`);
