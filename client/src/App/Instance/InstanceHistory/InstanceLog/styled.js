import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import {Document} from 'modules/components/Icon';
import BasicFlowNodeIcon from 'modules/components/FlowNodeIcon';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const InstanceLog = themed(styled.ul`
  flex: 1;
  display: flex;
  flex-direction: column;
  list-style-type: none;
  margin: 0;
  padding: 0;

  overflow: auto;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  border-top: none;
  border-bottom: none;
`);

const backgroundColorStyle = css`
  &:nth-child(odd) {
    background-color: ${({theme, isSelected}) => {
      if (isSelected) {
        return Colors.selections;
      } else if (theme === 'dark') {
        return Colors.uiDark02;
      } else {
        return Colors.lightItemOdd;
      }
    }};
  }

  &:nth-child(even) {
    background-color: ${({theme, isSelected}) => {
      if (isSelected) {
        return Colors.selections;
      } else if (theme === 'dark') {
        return Colors.darkItemEven;
      } else {
        return Colors.lightItemEven;
      }
    }};
  }
`;

const colorStyle = css`
  color: ${({theme, isSelected, state}) => {
    return isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : Colors.uiDark04;
  }};
`;

const iconPositionStyle = css`
  position: relative;
  top: 3px;
`;

export const LogEntry = styled.li`
  position: relative;
  height: 32px;
`;

export const LogEntryToggle = themed(styled.button`
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%:
  background: transparent;
  margin: 0;
  border: none;
  padding: 5px;
  padding-left: 43px;
  text-align: left;

  ${backgroundColorStyle};
  ${colorStyle};

  cursor: pointer;

  font-size: 14px;
  font-weight: ${({isSelected}) => (!!isSelected ? 'bold' : 'normal')};
`);

export const HeaderToggle = themed(styled(LogEntryToggle)`
  padding-left: 23px;
  color: ${({theme, isSelected}) => {
    return isSelected || theme === 'dark'
      ? 'rgba(255, 255, 255, 0.9)'
      : 'rgba(69, 70, 78, 0.9)';
  }};
  font-weight: bold;
`);

export const DocumentIcon = themed(styled(
  withStrippedProps(['isSelected'])(Document)
)`
  width: 16px;
  height: 16px;
  margin-right: 8px;

  ${colorStyle};
  ${iconPositionStyle};
`);

export const FlowNodeIcon = themed(styled(
  withStrippedProps(['isSelected'])(BasicFlowNodeIcon)
)`
  margin-right: 8px;

  ${colorStyle};
  ${iconPositionStyle};
`);
