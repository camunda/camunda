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
//
// No theme prop: the DS Toaster reads light/dark off the ambient
// .c4-ui/.dark scope (see C4Provider, mounted once at the app root in
// main.tsx) the same way every other DS component does, instead of the raw
// sonner Toaster's own theme prop this used to need.
import {Toaster} from '@camunda/design-system';

const AppToaster: React.FC = () => <Toaster position="top-right" />;

export {AppToaster};
