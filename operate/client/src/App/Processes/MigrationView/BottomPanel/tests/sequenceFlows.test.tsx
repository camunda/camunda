/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {BottomPanel} from '../';
import {open} from 'modules/mocks/diagrams';
import {SOURCE_PROCESS_DEFINITION_KEY, Wrapper} from './mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {searchResult} from 'modules/testUtils';

const TARGET_PROCESS_DEFINITION_KEY = '2';
const HEADER_ROW_COUNT = 1;
const MAPPABLE_ITEMS_ROW_COUNT = 13;
const AUTO_MAPPABLE_ITEMS_ROW_COUNT = 3;

describe('BottomPanel - sequence flow mappings', () => {
  beforeEach(async () => {
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        {
          processDefinitionId: 'SequenceFlowMigration',
          processDefinitionKey: SOURCE_PROCESS_DEFINITION_KEY,
          version: 1,
          name: 'SequenceFlowMigration',
          versionTag: '',
          tenantId: '<default>',
          hasStartForm: false,
        },
        {
          processDefinitionId: 'SequenceFlowMigration',
          processDefinitionKey: TARGET_PROCESS_DEFINITION_KEY,
          version: 2,
          name: 'SequenceFlowMigration',
          versionTag: '',
          tenantId: '<default>',
          hasStartForm: false,
        },
      ]),
    );

    mockFetchProcessDefinitionXml({
      processDefinitionKey: SOURCE_PROCESS_DEFINITION_KEY,
    }).withSuccess(open('SequenceFlowMigration_v1.bpmn'));
    mockFetchProcessDefinitionXml({
      processDefinitionKey: TARGET_PROCESS_DEFINITION_KEY,
    }).withSuccess(open('SequenceFlowMigration_v2.bpmn'));

    vi.stubGlobal('location', {
      ...window.location,
      search: '?process=SequenceFlowMigration&version=1',
    });

    await processesStore.fetchProcesses();
    processesStore.setSelectedTargetProcess(
      '{SequenceFlowMigration}-{<default>}',
    );
    processesStore.setSelectedTargetVersion(2);
  });

  it('should show mappable sequence flows', async () => {
    render(<BottomPanel />, {wrapper: Wrapper});

    // Expect all sequence flows leading to merging parallel or inclusive gateways to be visible
    expect(
      await screen.findByRole('combobox', {
        name: /target element for Gateway_0bcfno8/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Gateway_0etv923/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Gateway_1sxij6y/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Activity_1mkmfoa/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Gateway_0h6a6k2/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Gateway_07izaz5/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Flow R/i,
      }),
    ).toBeVisible();

    expect(
      screen.getByRole('combobox', {
        name: /target element for Flow H/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Flow D/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Flow K/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Flow L/i,
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: /target element for Flow N/i,
      }),
    ).toBeVisible();

    // Expect no sequence flows leading to exclusive gateway
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow Q`, 'i'),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow T`, 'i'),
      }),
    ).not.toBeInTheDocument();

    // Expect no sequence flows leading to splitting gateways
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow A`, 'i'),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow B`, 'i'),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow G`, 'i'),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow I`, 'i'),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow O`, 'i'),
      }),
    ).not.toBeInTheDocument();

    // Expect all mappable sequence flows to be visible
    expect(await screen.findAllByRole('row')).toHaveLength(
      HEADER_ROW_COUNT + MAPPABLE_ITEMS_ROW_COUNT,
    );
  });

  it('should show auto-mappable sequence flows', async () => {
    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    // Wait until rows are rendered
    expect(
      await screen.findByRole('combobox', {
        name: new RegExp(`target element for Flow D`, 'i'),
      }),
    ).toBeVisible();

    // Toggle on unmapped flow nodes
    await user.click(screen.getByLabelText(/show only not mapped/i));

    // Expect not mapped items to be visible
    expect(
      screen.getByRole('combobox', {
        name: new RegExp(`target element for Flow H`, 'i'),
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: new RegExp(`target element for Flow D`, 'i'),
      }),
    ).toBeVisible();
    expect(
      screen.getByRole('combobox', {
        name: new RegExp(`target element for Flow K`, 'i'),
      }),
    ).toBeVisible();

    // Expect auto-mappable items to be hidden
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow L`, 'i'),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow N`, 'i'),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('combobox', {
        name: new RegExp(`target element for Flow R`, 'i'),
      }),
    ).not.toBeInTheDocument();

    expect(await screen.findAllByRole('row')).toHaveLength(
      HEADER_ROW_COUNT + AUTO_MAPPABLE_ITEMS_ROW_COUNT,
    );
  });

  it('should show correct target elements', async () => {
    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    // Wait until rows are rendered
    expect(
      await screen.findByRole('combobox', {
        name: /target element for Flow D/i,
      }),
    ).toBeVisible();

    await user.click(
      screen.getByRole('combobox', {
        name: /target element for Flow H/i,
      }),
    );

    const options = screen.getByRole('combobox', {
      name: /target element for Flow H/i,
    }).children;

    expect(options).toHaveLength(6);
    expect(options[0].textContent).toMatch('');
    expect(options[1].textContent).toMatch(/Flow R/i);
    expect(options[2].textContent).toMatch(/Flow C/i);
    expect(options[3].textContent).toMatch(/Flow L/i);
    expect(options[4].textContent).toMatch(/Flow N/i);
    expect(options[5].textContent).toMatch(/Flow U/i);
  });
});
