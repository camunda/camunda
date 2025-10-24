/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {FormJSRenderer} from '.';
import * as formSchemaMocks from 'common/mocks/form-schema';
import {FormManager} from 'common/form-js/formManager';

function noop() {
  return Promise.resolve();
}

describe('<FormJSRenderer />', () => {
  beforeEach(() => {
    global.IntersectionObserver = vi.fn(() => ({
      observe: vi.fn(),
      unobserve: vi.fn(),
      disconnect: vi.fn(),
      root: null,
      rootMargin: '',
      thresholds: [],
      takeRecords: () => [],
    }));
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should merge variables on submit', async () => {
    vi.useFakeTimers({
      shouldAdvanceTime: true,
    });
    let formManager: FormManager | null = null;
    const variables = {
      root: {
        nested: {
          test_field: 'foo',
          randomVar1: 'test',
        },
        name: 'Julius',
      },
      randomVar2: 'test_a',
    };
    const handleSubmit = vi.fn();

    const {user} = render(
      <FormJSRenderer
        handleSubmit={handleSubmit}
        schema={formSchemaMocks.nestedForm}
        data={variables}
        onMount={(manager) => {
          formManager = manager;
        }}
        onRender={noop}
        onImportError={noop}
        onSubmitStart={noop}
        onSubmitError={noop}
        onSubmitSuccess={noop}
        onValidationError={noop}
      />,
    );

    expect(await screen.findByLabelText(/surname/i)).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/surname/i));
    vi.runOnlyPendingTimers();
    await user.type(screen.getByLabelText(/surname/i), 'bar');
    vi.runOnlyPendingTimers();

    formManager!.submit();

    expect(handleSubmit).toHaveBeenCalledWith([
      {
        name: 'root',
        value: JSON.stringify({
          nested: {
            test_field: 'bar',
            randomVar1: 'test',
          },
          name: 'Julius',
        }),
      },
    ]);

    vi.useRealTimers();
  });

  it('should inject document service endpoint to preview documents', async () => {
    render(
      <FormJSRenderer
        handleSubmit={noop}
        schema={formSchemaMocks.documentPreview}
        data={{
          myDocuments: [
            {
              'camunda.document.type': 'camunda',
              storeId: 'in-memory',
              documentId: '8add9f73-776d-451d-81d0-fc167d4220c0',
              metadata: {
                contentType: 'application/octet-stream',
                fileName: 'document0',
                size: 663849,
                customProperties: {},
              },
            },
            {
              'camunda.document.type': 'camunda',
              storeId: 'in-memory',
              documentId: '2ee85de7-ed39-4620-81aa-df73ccfd0344',
              metadata: {
                contentType: 'application/pdf',
                fileName: 'Onboarding Guide.pdf',
                size: 546904,
                customProperties: {},
              },
            },
            {
              'camunda.document.type': 'camunda',
              storeId: 'in-memory',
              documentId: 'e2b09092-7994-4813-b1ef-eb29731aea3d',
              metadata: {
                contentType: 'application/pdf',
                fileName: '766-st-1-vinicius-goulart.pdf',
                size: 39993,
                customProperties: {},
              },
            },
          ],
        }}
        onMount={noop}
        onRender={noop}
        onImportError={noop}
        onSubmitStart={noop}
        onSubmitError={noop}
        onSubmitSuccess={noop}
        onValidationError={noop}
      />,
    );

    expect(await screen.findByText('My documents')).toBeInTheDocument();

    expect(screen.getByText('document0')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Download document0'}),
    ).toBeInTheDocument();
    expect(screen.getByText('Onboarding Guide.pdf')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Download Onboarding Guide.pdf'}),
    ).toBeInTheDocument();
    expect(
      screen.getByText('766-st-1-vinicius-goulart.pdf'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: 'Download 766-st-1-vinicius-goulart.pdf',
      }),
    ).toBeInTheDocument();
  });
});
