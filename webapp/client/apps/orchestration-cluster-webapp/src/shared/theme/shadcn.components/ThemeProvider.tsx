/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {C4Provider, Toaster, useTheme} from '@camunda/design-system';
import '@camunda/design-system/styles.css';
import {observer} from 'mobx-react-lite';
import {themeStore} from '#/shared/theme/theme';

type Props = {
	children: React.ReactNode;
};

const ThemeProvider = observer(({children}: Props) => {
	const {resolvedTheme} = useTheme(themeStore.selectedTheme);

	return (
		<C4Provider theme={resolvedTheme}>
			<Toaster position="top-right" />
			{children}
		</C4Provider>
	);
});

export {ThemeProvider};
