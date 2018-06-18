import React from 'react';
import styled from 'styled-components';
import {ThemeConsumer} from 'modules/theme';

export const Toggle = styled.button`
  position: fixed;
  bottom: 0;
  left: 0;
  background: black;
`;

// Utility component to test the theming during development;
export default function ThemeToggle() {
  return (
    <ThemeConsumer>
      {({toggleTheme}) => (
        <React.Fragment>
          <Toggle onClick={toggleTheme}>x</Toggle>
        </React.Fragment>
      )}
    </ThemeConsumer>
  );
}
