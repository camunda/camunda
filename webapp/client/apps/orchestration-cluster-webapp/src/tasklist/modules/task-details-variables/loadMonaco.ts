/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {loader} from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import jsonWorker from 'monaco-editor/language/json/json.worker.js?worker';
import editorWorker from 'monaco-editor/editor/editor.worker.js?worker';

function loadMonaco() {
	self.MonacoEnvironment = {
		getWorker(_, label: string) {
			return label === 'json' ? new jsonWorker() : new editorWorker();
		},
	};

	monaco.json.jsonDefaults.setDiagnosticsOptions({
		...monaco.json.jsonDefaults.diagnosticsOptions,
		schemaValidation: 'error',
		schemaRequest: 'error',
	});
	loader.config({monaco});
}

export {loadMonaco};
