/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, type ComponentProps} from 'react';
import {LoaderCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';

const LazyMonacoDiffEditor = lazy(async () => {
	const [{loadMonaco}, {MonacoDiffEditor}] = await Promise.all([
		import('#/shared/monaco/loadMonaco'),
		import('#/operate/shared/Editors/MonacoDiffEditor'),
	]);
	loadMonaco();
	return {default: MonacoDiffEditor};
});

const DiffEditor = (props: ComponentProps<typeof LazyMonacoDiffEditor>) => {
	const {t} = useTranslation();

	return (
		<Suspense
			fallback={
				<div role="status" className="flex items-center gap-2 p-2 text-sm text-muted-foreground">
					<LoaderCircle className="size-4 animate-spin" aria-hidden />
					{t('operate.shared.editors.loading')}
				</div>
			}
		>
			<LazyMonacoDiffEditor {...props} />
		</Suspense>
	);
};

export {DiffEditor};
