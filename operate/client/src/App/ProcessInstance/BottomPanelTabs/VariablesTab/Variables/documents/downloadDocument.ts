/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DocumentReference} from './types';
import {MOCK_DOCUMENT_ASSET_PATHS} from './mockDocumentVariables';

// TODO: Replace with API call to GET /v2/documents/{id}/content that streams
// the binary, then trigger download from the resulting blob URL.
function downloadDocument(document: DocumentReference): void {
  const path = MOCK_DOCUMENT_ASSET_PATHS[document.documentId];
  if (path === undefined) {
    return;
  }
  const link = window.document.createElement('a');
  link.href = path;
  link.download = document.metadata.fileName;
  window.document.body.appendChild(link);
  link.click();
  window.document.body.removeChild(link);
}

function downloadVariableAsJson(variableName: string, value: string): void {
  const pretty = (() => {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  })();
  const blob = new Blob([pretty], {type: 'application/json'});
  const url = URL.createObjectURL(blob);
  const link = window.document.createElement('a');
  link.href = url;
  link.download = `${variableName}.json`;
  window.document.body.appendChild(link);
  link.click();
  window.document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export {downloadDocument, downloadVariableAsJson};
