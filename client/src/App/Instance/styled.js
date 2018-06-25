import styled from 'styled-components';
import SplitPane from 'modules/components/SplitPane';

import {HEADER_HEIGHT} from './../Header/styled';

export const Instance = styled.div`
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${HEADER_HEIGHT}px);
  position: relative;

  /* prevents header dropdown to not go under the content */
  /* display: flex has z-index as well */
  z-index: 0;
`;

export const Top = styled.div`
  flex-grow: 1;
  display: flex;
`;

export const Bottom = styled.div`
  flex-grow: 1;
  display: flex;
`;

export const PaneFooter = styled(SplitPane.Pane.Footer)`
  display: flex;
  justify-content: flex-end;
`;
