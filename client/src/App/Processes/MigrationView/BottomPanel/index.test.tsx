/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {useEffect} from 'react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {BottomPanel} from '.';
import {open} from 'modules/mocks/diagrams';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockProcessXML} from 'modules/testUtils';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  processXmlMigrationSourceStore.setProcessXml(open('orderProcess.bpmn'));
  processInstanceMigrationStore.enable();

  useEffect(() => {
    processInstanceMigrationStore.reset();
    processXmlMigrationSourceStore.reset();
    processXmlMigrationTargetStore.reset();
    processStatisticsStore.reset();
  });
  return (
    <>
      {children}
      <button
        onClick={() => {
          processXmlMigrationTargetStore.fetchProcessXml();
        }}
      >
        Fetch Target Process
      </button>
    </>
  );
};

describe('MigrationView/BottomPanel', () => {
  it('should render source flow nodes', async () => {
    render(<BottomPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByRole('cell', {name: /^request for payment$/i}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: /^ship articles$/i}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: /^check payment$/i}),
    ).toBeInTheDocument();

    // expect table to have 1 header + 3 content rows
    expect(screen.getAllByRole('row')).toHaveLength(4);
  });

  it('should render target flow nodes', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    const combobox = await screen.findByRole('combobox', {
      name: /^target flow node for request for payment$/i,
    });

    expect(combobox).toBeDisabled();

    screen.getByRole('button', {name: /fetch target process/i}).click();
    await waitFor(() => {
      expect(combobox).toBeEnabled();
    });

    await user.selectOptions(combobox, 'ServiceTask_0kt6c5i');
    expect(combobox).toHaveValue('ServiceTask_0kt6c5i');

    await user.selectOptions(combobox, '');
    expect(combobox).toHaveValue('');
  });
});
