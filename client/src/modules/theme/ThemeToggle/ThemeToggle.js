import React from 'react';
import styled from 'styled-components';
import {ThemeConsumer, Colors, themed, themeStyle} from 'modules/theme';

export const Toggle = themed(styled.button`
  position: fixed;
  padding: 10px;
  bottom: 0;
  left: 0;
  background: ${themeStyle({
    dark: Colors.uiLight01,
    light: Colors.uiDark01
  })};
`);

// Utility component to test the theming during development;
export default function ThemeToggle() {
  return (
    <ThemeConsumer>
      {({toggleTheme}) => (
        <React.Fragment>
          <Toggle onClick={toggleTheme} />
        </React.Fragment>
      )}
    </ThemeConsumer>
  );
}
