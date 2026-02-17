/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CodeSnippetProps} from '@carbon/react';
import {DarkSnippet, LightSnippet} from './styled.tsx';

type Props = CodeSnippetProps & {
  theme?: 'light' | 'dark';
  children: React.ReactNode;
};

const Snippet: React.FC<Props> = ({
  theme = 'light',
  children,
  ...codeSnippetProps
}) => {
  const StyledSnippet = theme === 'light' ? LightSnippet : DarkSnippet;
  return <StyledSnippet {...codeSnippetProps}>{children}</StyledSnippet>;
};

export {Snippet};
