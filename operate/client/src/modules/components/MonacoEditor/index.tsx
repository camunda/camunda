/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import Editor, {DiffEditor, useMonaco, loader} from '@monaco-editor/react';
import * as monaco from 'monaco-editor';

loader.config({monaco});

export default Editor;
export {useMonaco, DiffEditor};
