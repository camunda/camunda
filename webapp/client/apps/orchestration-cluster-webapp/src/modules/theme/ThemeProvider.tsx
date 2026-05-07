/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ThemeProvider as DSThemeProvider} from '@camunda/design-system';
import {observer} from 'mobx-react-lite';
import type {ReactNode} from 'react';
import {themeStore} from './theme';

type Props = {
	children: ReactNode;
};

const ThemeProvider: React.FC<Props> = observer(({children}) => (
	<DSThemeProvider theme={themeStore.actualTheme}>{children}</DSThemeProvider>
));

export {ThemeProvider};
