/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {hasFileComponents} from './hasFileComponents';

describe('hasFileComponents', () => {
  it('should detect file pickers', () => {
    expect(
      hasFileComponents({
        components: [
          {
            type: 'filepicker',
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'group',
            components: [
              {
                type: 'filepicker',
              },
            ],
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'dynamiclist',
            components: [
              {
                type: 'filepicker',
              },
            ],
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'group',
            components: [
              {
                type: 'dynamiclist',
                components: [
                  {
                    type: 'filepicker',
                  },
                ],
              },
            ],
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'dynamiclist',
            components: [
              {
                type: 'group',
                components: [
                  {
                    type: 'filepicker',
                  },
                ],
              },
            ],
          },
        ],
      }),
    ).toBe(true);
  });

  it('should detect document previews', () => {
    expect(
      hasFileComponents({
        components: [
          {
            type: 'documentPreview',
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'group',
            components: [
              {
                type: 'documentPreview',
              },
            ],
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'dynamiclist',
            components: [
              {
                type: 'documentPreview',
              },
            ],
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'group',
            components: [
              {
                type: 'dynamiclist',
                components: [
                  {
                    type: 'documentPreview',
                  },
                ],
              },
            ],
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'dynamiclist',
            components: [
              {
                type: 'group',
                components: [
                  {
                    type: 'documentPreview',
                  },
                ],
              },
            ],
          },
        ],
      }),
    ).toBe(true);
  });

  it('should detect file pickers and document previews at the same time', () => {
    expect(
      hasFileComponents({
        components: [
          {
            type: 'filepicker',
          },
          {
            type: 'documentPreview',
          },
        ],
      }),
    ).toBe(true);
    expect(
      hasFileComponents({
        components: [
          {
            type: 'documentPreview',
          },
          {
            type: 'filepicker',
          },
        ],
      }),
    ).toBe(true);
  });

  it('should handle invalid schemas', () => {
    expect(hasFileComponents({})).toBe(false);
    expect(hasFileComponents({components: null})).toBe(false);
    expect(hasFileComponents({components: {}})).toBe(false);
    expect(hasFileComponents({components: [{}]})).toBe(false);
  });

  it('should handle empty components array', () => {
    expect(
      hasFileComponents({
        components: [],
      }),
    ).toBe(false);
  });

  it('should handle mix file and non-file components', () => {
    expect(
      hasFileComponents({
        components: [
          {
            type: 'textfield',
          },
          {
            type: 'group',
            components: [
              {
                type: 'number',
              },
              {
                type: 'filepicker',
              },
            ],
          },
          {
            type: 'checkbox',
          },
        ],
      }),
    ).toBe(true);
  });

  it('should handle non-file components', () => {
    expect(
      hasFileComponents({
        components: [
          {
            type: 'textfield',
          },
          {
            type: 'group',
            components: [
              {
                type: 'number',
              },
            ],
          },
          {
            type: 'checkbox',
          },
        ],
      }),
    ).toBe(false);
  });
});
