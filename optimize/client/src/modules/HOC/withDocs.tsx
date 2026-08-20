/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, ComponentType, ReactNode} from 'react';

import {useDocs} from 'hooks';

export interface WithDocsProps {
  generateDocsLink: (path: string) => string;
  getBaseDocsUrl: () => string;
}

const DOCS_BASE_URL = 'https://docs.camunda.io/';
const OPTIMIZE_DOCS_URL = DOCS_BASE_URL + 'optimize/';

export const DocsContext = createContext<WithDocsProps>({
  generateDocsLink: (path) => OPTIMIZE_DOCS_URL + path,
  getBaseDocsUrl: () => DOCS_BASE_URL,
});

export function DocsProvider({children}: {children: ReactNode}): JSX.Element {
  const generateDocsLink = (path: string) => OPTIMIZE_DOCS_URL + path;
  const getBaseDocsUrl = () => DOCS_BASE_URL;

  return (
    <DocsContext.Provider value={{generateDocsLink, getBaseDocsUrl}}>
      {children}
    </DocsContext.Provider>
  );
}

export default function withDocs<T extends object>(
  Component: ComponentType<T>
): ComponentType<Omit<T, keyof WithDocsProps>> {
  const Wrapper = (props: Omit<T, keyof WithDocsProps>) => (
    <Component {...useDocs()} {...(props as T)} />
  );

  Wrapper.displayName = `${Component.displayName || Component.name || 'Component'}DocsHandler`;

  Wrapper.WrappedComponent = Component;

  return Wrapper;
}
