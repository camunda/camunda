/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { createContext, FC, ReactNode, useContext } from "react";

// Use stable as default. Should be overridden pointing to actual product version.
const DocsUrlContext = createContext<string | undefined>(undefined);

type DocsUrlProviderProps = {
  children: ReactNode;
  value: string;
};

export const DocsUrlProvider: FC<DocsUrlProviderProps> = ({
  children,
  value,
}) => (
  <DocsUrlContext.Provider value={value}>{children}</DocsUrlContext.Provider>
);

export const useDocsUrl = (): string => {
  let contextValue = useContext(DocsUrlContext);
  if (contextValue === undefined) {
    throw new Error("no docs url defined");
  }
  return contextValue;
};
