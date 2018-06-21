import React from 'react';

import {Colors} from './index';

const THEME_NAME = {
  LIGHT: 'light',
  DARK: 'dark'
};
// Create a context for the current theme
const ThemeContext = React.createContext();

// Wrapper that passes the theme as a prop
const ThemeConsumer = ThemeContext.Consumer;

// Top level component to pass down theme in the App
class ThemeProvider extends React.Component {
  constructor(props) {
    super(props);
    this.setBodyBackground();
  }

  // we start with the dark theme as default
  state = {theme: THEME_NAME.DARK};

  toggleTheme = () => {
    this.setState({
      theme:
        this.state.theme === THEME_NAME.DARK
          ? THEME_NAME.LIGHT
          : THEME_NAME.DARK
    });
  };

  setBodyBackground = () => {
    const {theme} = this.state;
    document.body.style.backgroundColor =
      theme === THEME_NAME.DARK ? Colors.uiDark01 : Colors.uiLight01;
  };

  render() {
    const contextValue = {...this.state, toggleTheme: this.toggleTheme};
    return (
      <ThemeContext.Provider value={contextValue}>
        {this.props.children}
      </ThemeContext.Provider>
    );
  }

  componentDidUpdate() {
    this.setBodyBackground();
  }
}

const themed = Component => {
  function Themed(props) {
    return (
      <ThemeConsumer>
        {({theme}) => <Component {...props} theme={theme} />}
      </ThemeConsumer>
    );
  }

  Themed.WrappedComponent = Component;

  Themed.displayName = `Themed(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return Themed;
};

const themeStyle = config => ({theme}) => config[theme];

export {ThemeConsumer, ThemeProvider, themed, themeStyle};
