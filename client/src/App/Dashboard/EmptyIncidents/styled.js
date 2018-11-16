import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {ReactComponent as Check} from 'modules/components/Icon/check.svg';
import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

export const EmptyIncidents = themed(styled.div`
  position: absolute;
  height: 100%;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border-width: 1px;
  border-style: solid;
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};
`);

export const Label = themed(styled.span`
  color: ${({type}) => {
    return type === 'success' ? Colors.allIsWell : Colors.incidentsAndErrors;
  }};
  opacity: 0.9;
  font-family: IBMPlexSans;
  font-size: 16px;
`);

export const CheckIcon = styled(Check)`
  width: 18px;
  height: 14px;
  fill: ${Colors.allIsWell};
  margin-right: 13px;
`;
export const WarningIcon = styled(Warning)`
  width: 20px;
  height: 18px;
  fill: ${Colors.incidentsAndErrors};
  margin-right: 15px;
`;
