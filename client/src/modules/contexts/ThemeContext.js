/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import withSharedState from 'modules/components/withSharedState';

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
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  constructor(props) {
    super(props);
    this.setBodyBackground();
  }

  // we start with the light theme as default
  state = {theme: THEME_NAME.LIGHT};

  componentDidMount() {
    const localStorage = this.props.getStateLocally('theme');

    if (localStorage.theme) {
      this.setState({theme: localStorage.theme});
    }
  }

  componentDidUpdate() {
    this.setBodyBackground();
  }

  toggleTheme = () => {
    const newTheme =
      this.state.theme === THEME_NAME.DARK ? THEME_NAME.LIGHT : THEME_NAME.DARK;

    this.props.storeStateLocally(
      {
        theme: newTheme
      },
      'theme'
    );

    this.setState({
      theme: newTheme
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
          {props => (
            <Component
              ref={forwardedRef}
              {...rest}
              theme={props ? props.theme : 'dark'}
            />
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

const WrappedThemeProvider = withSharedState(ThemeProvider);
WrappedThemeProvider.WrappedComponent = ThemeProvider;

export {
  ThemeConsumer,
  WrappedThemeProvider as ThemeProvider,
  themed,
  themeStyle
};
