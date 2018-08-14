import styled from 'styled-components';
import {Colors, themed} from 'modules/theme';

export const SelectionButton = themed(styled.div`
  font-family: IBMPlexSans;
  font-size: 13px;
  padding: 4px 11px 5px 11px;
  font-weight: 600;
  cursor: pointer;
  background: ${Colors.selections};
  height: 26px;
  border-radius: 13px;
  margin-bottom: 6px;
  border: none;
  color: rgba(255, 255, 255, 1);
  float: left;

  & > svg {
    vertical-align: text-bottom;
    margin-right: 8px;
  }
`);
