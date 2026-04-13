/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {loader} from '@monaco-editor/react';
import 'monaco-editor/esm/vs/language/json/monaco.contribution.js';
import 'monaco-editor/esm/vs/editor/browser/coreCommands.js';
import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController.js';
import * as monaco from 'monaco-editor';
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

function loadMonaco() {
  self.MonacoEnvironment = {
    getWorker(_, label: string) {
      if (label === 'json') {
        return new jsonWorker();
      }
      return new editorWorker();
    },
  };

  monaco.json.jsonDefaults.setDiagnosticsOptions({
    ...monaco.json.jsonDefaults.diagnosticsOptions,
    schemaValidation: 'error',
    schemaRequest: 'error',
  });

  loader.config({
    monaco,
  });
}

export {loadMonaco};
