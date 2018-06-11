import styled from 'styled-components';
import {Colors} from 'theme';

export const Label = styled.button`
  background: none;
  border: none;
  color: currentColor;
  outline: none;
  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
`;

export const Dropdown = styled.div`
  display: inline-block;
  position: relative;
`;

export const DropdownMenu = styled.div`
  background-color: ${Colors.uiDark04};
  border: 1px solid ${Colors.uiDark06};
  border-radius: 3px;
  box-shadow: 0 2px 2px 0 rgba(0, 0, 0, 0.5);
  min-width: 186px;
  position: absolute;
  right: 0;
  margin-top: 5px;

  &:after,
  &:before {
    bottom: 100%;
    right: 15px;
    border: solid transparent;
    content: ' ';
    position: absolute;
    pointer-events: none;
  }

  &:after {
    border-bottom-color: ${Colors.uiDark04};
    border-width: 7px;
    margin-right: -7px;
  }
  &:before {
    border-bottom-color: ${Colors.uiDark06};
    border-width: 8px;
    margin-right: -8px;
  }
`;

export const Option = styled.button`
  height: 36px;
  background: none;
  border: none;
  color: currentColor;
  outline: none;
  cursor: pointer;
  width: 100%;
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;
  padding: 0 10px;
`;
