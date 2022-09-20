/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper, GROUPED_PROCESSES} from './mocks';
import {IS_DATE_RANGE_FILTERS_ENABLED} from 'modules/feature-flags';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {mockProcessXML} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes';
import {processDiagramStore} from 'modules/stores/processDiagram';

import {Filters} from '../index';

describe('Validations', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(GROUPED_PROCESSES))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );

    processesStore.fetchProcesses();

    await processDiagramStore.fetchProcessDiagram('bigVarProcess');
    jest.useFakeTimers();
  });

  afterEach(() => {
    processesStore.reset();
    processDiagramStore.reset();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should validate process instance keys', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/process instance key\(s\)/i), 'a');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/process instance key\(s\)/i));

    expect(
      screen.queryByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/process instance key\(s\)/i), '1');

    expect(
      await screen.findByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/process instance key\(s\)/i));

    expect(
      screen.queryByText(
        'Key has to be a 16 to 19 digit number, separated by space or comma'
      )
    ).not.toBeInTheDocument();
  });

  it('should validate Parent Process Instance Key', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Parent Process Instance Key'));
    await user.type(screen.getByLabelText(/Parent Process Instance Key/i), 'a');

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/Parent Process Instance Key/i));

    expect(
      screen.queryByText('Key has to be a 16 to 19 digit number')
    ).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/Parent Process Instance Key/i), '1');

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/Parent Process Instance Key/i));

    expect(
      screen.queryByText('Key has to be a 16 to 19 digit number')
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/Parent Process Instance Key/i),
      '1111111111111111, 2222222222222222'
    );

    expect(
      await screen.findByText('Key has to be a 16 to 19 digit number')
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/Parent Process Instance Key/i));

    expect(
      screen.queryByText('Key has to be a 16 to 19 digit number')
    ).not.toBeInTheDocument();
  });

  (IS_DATE_RANGE_FILTERS_ENABLED ? it.skip : it)(
    'should validate start date',
    async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });
      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('Start Date'));
      await user.type(screen.getByLabelText(/start date/i), 'a');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByLabelText(/start date/i));

      expect(
        screen.queryByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText(/start date/i), '2021-05');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();
    }
  );

  (IS_DATE_RANGE_FILTERS_ENABLED ? it.skip : it)(
    'should validate end date',
    async () => {
      const {user} = render(<Filters />, {
        wrapper: getWrapper(),
      });

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.click(screen.getByText(/^more filters$/i));
      await user.click(screen.getByText('End Date'));
      await user.type(screen.getByLabelText(/end date/i), 'a');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();

      expect(screen.getByTestId('search')).toBeEmptyDOMElement();

      await user.clear(screen.getByLabelText(/end date/i));

      expect(
        screen.queryByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).not.toBeInTheDocument();

      await user.type(screen.getByLabelText(/end date/i), '2021-05');

      expect(
        await screen.findByText('Date has to be in format YYYY-MM-DD hh:mm:ss')
      ).toBeInTheDocument();
    }
  );

  it('should validate variable name', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Variable'));

    await user.type(screen.getByLabelText(/value/i), '"someValidValue"');

    expect(
      await screen.findByText('Name has to be filled')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/value/i));
    await user.type(screen.getByLabelText(/value/i), 'somethingInvalid');

    expect(
      await screen.findByText('Name has to be filled')
    ).toBeInTheDocument();

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('should validate variable value', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Variable'));

    await user.type(
      screen.getByTestId('optional-filter-variable-name'),
      'aRandomVariable'
    );

    expect(
      await screen.findByText('Value has to be filled')
    ).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByTestId('optional-filter-variable-name'));

    expect(
      screen.queryByText('Value has to be filled')
    ).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/value/i), 'invalidValue');

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();
    expect(
      await screen.findByText('Name has to be filled')
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.type(
      screen.getByTestId('optional-filter-variable-name'),
      'aRandomVariable'
    );

    expect(await screen.findByText('Value has to be JSON')).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('should validate operation id', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByText(/^more filters$/i));
    await user.click(screen.getByText('Operation Id'));

    await user.type(screen.getByLabelText(/operation id/i), 'g');

    expect(await screen.findByText('Id has to be a UUID')).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/operation id/i));

    expect(screen.queryByTitle('Id has to be a UUID')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/operation id/i), 'a');

    expect(await screen.findByText('Id has to be a UUID')).toBeInTheDocument();
  });
});
