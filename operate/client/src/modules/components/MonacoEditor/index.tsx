/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Editor, {DiffEditor, useMonaco, loader} from '@monaco-editor/react';
import * as monaco from 'monaco-editor';

loader.config({monaco});

export default Editor;
export {useMonaco, DiffEditor};
