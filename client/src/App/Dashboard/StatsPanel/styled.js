import styled from 'styled-components';
import {Colors} from 'theme';

export const Panel = styled.div`
  padding: 66px 0;
  border-radius: 3px;
  background-color: ${Colors.uiDark02};
  border: solid 1px ${Colors.uiDark04};
`;

export const Ul = styled.ul`
  padding: 0px;
  margin: 0px;
  list-style-type: none;
  display: flex;
  justify-content: space-around;
  & > li {
    width: 100%;
    &:not(:last-child) {
      border-right: solid 1px ${Colors.uiDark05};
    }
  }
`;
