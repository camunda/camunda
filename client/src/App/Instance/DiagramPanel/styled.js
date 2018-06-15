import styled from 'styled-components';

import {Colors} from 'modules/theme';

export const DiagramPanel = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  width: 100%;
  height: 100%;
`;

export const DiagramPanelHeader = styled.table`
  width: 100%;
`;

export const IncidentMessage = styled.div`
  background-color: ${Colors.incidentsAndErrors};
  color: #ffffff;
  padding: 9px 20px;
  font-size: 14px;
`;
