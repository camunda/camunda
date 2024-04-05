/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {FormJSRenderer} from '.';
import * as formMocks from 'modules/mock-schema/mocks/form';
import {FormManager} from 'modules/formManager';

function noop() {
  return Promise.resolve();
}

describe('<FormJSRenderer />', async () => {
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
        schema={formMocks.nestedForm.schema}
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
});
