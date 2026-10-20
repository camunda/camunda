/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {themeEditorStore} from '#/tasklist/modules/theme-editor/themeEditorStore';
import {AppLogo} from './AppLogo';

/** The header logo in the preview, which follows the draft theme instead of the applied one. */
const PreviewLogo: React.FC<{onWidthChange: (width: number) => void}> = observer(({onWidthChange}) => (
	<AppLogo logo={themeEditorStore.config.logo} onWidthChange={onWidthChange} />
));

export {PreviewLogo};
