import React from 'react';

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
  toggleTheme = () => {
    this.setState({
      theme:
        this.state.theme === THEME_NAME.DARK
          ? THEME_NAME.LIGHT
          : THEME_NAME.DARK
    });
  };

  // we start with the dark theme as default
  state = {theme: THEME_NAME.DARK, toggleTheme: this.toggleTheme};

  render() {
    return (
      <ThemeContext.Provider value={this.state}>
        {this.props.children}
      </ThemeContext.Provider>
    );
  }
}

const themed = StyledComponent => props => (
  <ThemeConsumer>
    {({theme}) => <StyledComponent theme={theme} {...props} />}
  </ThemeConsumer>
);

export {ThemeConsumer, ThemeProvider, themed};
