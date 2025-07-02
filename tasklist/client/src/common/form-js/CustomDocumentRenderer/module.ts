/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CustomDocumentRenderer} from './index';

// This class will register our custom DocumentRenderer to override the default one
class CustomDocumentRendererRegistrar {
  constructor(formFields: any) {
    // Override the default documentPreview component
    formFields.register('documentPreview', CustomDocumentRenderer);
  }
}

// Inject formFields service
(CustomDocumentRendererRegistrar as any).$inject = ['formFields'];

// Module to override the default DocumentRenderer with our custom one
const CamundaDocumentRendererModule = {
  __init__: ['customDocumentRendererRegistrar'],
  customDocumentRendererRegistrar: ['type', CustomDocumentRendererRegistrar],
};

export {CamundaDocumentRendererModule};