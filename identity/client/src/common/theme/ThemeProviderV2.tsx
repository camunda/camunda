/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { observer } from "mobx-react-lite";
import { themeStore } from "src/common/theme/theme";
import { C4Provider, useTheme } from "@camunda/design-system";

type Props = {
  children: React.ReactNode;
};

const ThemeProvider: React.FC<Props> = observer(({ children }) => {
  const { resolvedTheme } = useTheme(themeStore.selectedTheme);

  return <C4Provider theme={resolvedTheme}>{children}</C4Provider>;
});

export { ThemeProvider };
