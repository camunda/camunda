/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CustomDocumentRenderer} from './index';

describe('CustomDocumentRenderer', () => {
  it('should have correct configuration', () => {
    expect(CustomDocumentRenderer.config).toBeDefined();
    expect(CustomDocumentRenderer.config.type).toBe('documentPreview');
    expect(CustomDocumentRenderer.config.keyed).toBe(false);
    expect(CustomDocumentRenderer.config.group).toBe('presentation');
    expect(CustomDocumentRenderer.config.name).toBe('Document preview');
  });

  it('should create default component with options', () => {
    const options = {customLabel: 'My Documents'};
    const component = CustomDocumentRenderer.config.create(options);
    
    expect(component.label).toBe('Document preview');
    expect(component.customLabel).toBe('My Documents');
  });

  it('should create default component without options', () => {
    const component = CustomDocumentRenderer.config.create();
    
    expect(component.label).toBe('Document preview');
  });
});