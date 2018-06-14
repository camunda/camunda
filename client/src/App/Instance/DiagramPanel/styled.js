import styled from 'styled-components';

import {Colors} from 'modules/theme';
import {StateIconIncident} from 'modules/components/Icon';

export const DiagramPanel = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  width: 100%;
  height: 100%;
`;

export const DiagramPanelBody = styled.div`
  flex-grow: 1;
  display: flex;
  flex-direction: column;
`;
export const DiagramPanelHeader = styled.table`
  width: 100%;
  table-layout: fixed;
`;

export const IncidentMessage = styled.div`
  background-color: ${Colors.incidentsAndErrors};
  color: #ffffff;
  padding: 9px 20px;
  font-size: 14px;
`;

export const IncidentIcon = styled(StateIconIncident)`
  position: relative;
  top: 3px;
  margin-right: 10px;
`;
