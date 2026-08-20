/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

export const themed = <P extends object>(
  Component: ComponentType<P>
): ComponentType<Omit<P, keyof ThemeContextProps>> => {
  const Wrapper = (props: Omit<P, keyof ThemeContextProps>) => {
    return <Consumer>{(themeProps) => <Component {...(props as P)} {...themeProps} />}</Consumer>;
  };

  Wrapper.WrappedComponent = Component;

  Wrapper.displayName = `Themed(${Component.displayName || Component.name || 'Component'})`;

  return Wrapper;
};
