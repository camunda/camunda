/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Component, ComponentType, ReactNode, createContext} from 'react';

interface ThemeState {
  theme: 'light' | 'dark';
}

export interface ThemeContextProps extends ThemeState {
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextProps | undefined>(undefined);

export class Provider extends Component<{children: ReactNode}, ThemeState> {
  state: ThemeState = {theme: 'light'};

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

export const themed = (Component: ComponentType<any>) => {
  function Themed(props: any) {
    return <Consumer>{(themeProps) => <Component {...props} {...themeProps} />}</Consumer>;
  }

  Themed.WrappedComponent = Component;

  Themed.displayName = `Themed(${Component.displayName || Component.name || 'Component'})`;

  return Themed;
};
