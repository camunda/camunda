import styled from 'styled-components';

import {Colors} from 'modules/theme';
import Panel from 'modules/components/Panel';
import {FlownodeEventIncident} from 'modules/components/Icon';

import {HEADER_HEIGHT} from './../Header/styled';

export const Instance = styled.div`
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${HEADER_HEIGHT}px);
`;

export const Top = styled.div`
  flex-grow: 1;
`;

export const Bottom = styled.div`
  flex-grow: 1;
`;

export const DiagramPanelHeader = styled(Panel.Header)`
  width: 100%;
  display: flex;
  justify-items: space-between;
`;

export const IncidentMessage = styled.div`
  background-color: ${Colors.incidentsAndErrors};
  color: #ffffff;
  padding: 9px 20px;
  font-size: 14px;
`;

export const IncidentIcon = styled(FlownodeEventIncident)`
  position: relative;
  top: 3px;
  margin-right: 4px;
`;
