import styled from 'styled-components';

import {Colors} from 'modules/theme';
import {StateIconIncident} from 'modules/components/Icon';
import Panel from 'modules/components/Panel';

export const DiagramPanel = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  width: 100%;
  height: 100%;
`;

export const DiagramPanelBody = styled(Panel.Body)`
  display: flex;
  flex-direction: column;
  background: blue;
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
