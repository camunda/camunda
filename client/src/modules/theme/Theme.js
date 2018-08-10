import React from 'react';

const ThemeContext = React.createContext();

export class Provider extends React.Component {
  state = {theme: 'light'};

  toggleTheme = () => {
    this.setState(({theme}) => {
      const newTheme = theme === 'dark' ? 'light' : 'dark';

      if (newTheme === 'dark') {
        document.body.classList.add('dark');
      } else {
        document.body.classList.remove('dark');
      }

      return {theme: newTheme};
    });
  };

  render() {
    const contextValue = {theme: this.state.theme, toggleTheme: this.toggleTheme};
    return (
      <ThemeContext.Provider value={contextValue}>{this.props.children}</ThemeContext.Provider>
    );
  }
}

export const Consumer = ThemeContext.Consumer;

export const themed = Component => {
  function Themed(props) {
    return <Consumer>{themeProps => <Component {...props} {...themeProps} />}</Consumer>;
  }

  Themed.WrappedComponent = Component;

  Themed.displayName = `Themed(${Component.displayName || Component.name || 'Component'})`;

  return Themed;
};
