import React from 'react';
import PropTypes from 'prop-types';

import {Colors} from 'modules/theme';

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
  static propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  constructor(props) {
    super(props);
    this.setBodyBackground();
  }

  // we start with the dark theme as default
  state = {theme: THEME_NAME.DARK};

  componentDidUpdate() {
    this.setBodyBackground();
  }

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
}

const themed = Component => {
  class Themed extends React.Component {
    render() {
      const {forwardedRef, ...rest} = this.props;
      return (
        <ThemeConsumer>
          {({theme}) => (
            <Component ref={forwardedRef} {...rest} theme={theme} />
          )}
        </ThemeConsumer>
      );
    }
  }

  const ThemedWithRef = React.forwardRef((props, ref) => {
    return <Themed {...props} forwardedRef={ref} />;
  });

  ThemedWithRef.WrappedComponent = Component;
  const name = Component.displayName || Component.name;
  ThemedWithRef.displayName = `Themed(${name})`;
  return ThemedWithRef;
};

const themeStyle = config => ({theme}) => config[theme];

export {ThemeConsumer, ThemeProvider, themed, themeStyle};
