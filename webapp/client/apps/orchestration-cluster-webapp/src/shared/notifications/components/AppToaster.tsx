/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Mount point for #/shared/notifications/toast.ts's DS-only toasts. Sits
// alongside <Notifications/> (unchanged, still serves Operate and the
// Carbon Tasklist path) — inert and empty until a DS Tasklist call site
// fires toast(). position="top-right" matches Notifications.module.scss's
// existing top-right placement.
import {observer} from 'mobx-react-lite';
import {Toaster} from 'sonner';
import {themeStore} from '#/shared/theme/theme';

const AppToaster: React.FC = observer(() => (
	<Toaster theme={themeStore.selectedTheme} position="top-right" richColors />
));

export {AppToaster};
