/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Bar} from './index';
import {mockStartNode, mockStartEventBusinessObject} from './index.setup';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';

describe('<Bar />', () => {
  afterEach(() => {
    flowNodeTimeStampStore.reset();
  });

  it('should show an icon based on node type and the node name', () => {
    render(
      <Bar
        flowNodeInstance={mockStartNode}
        businessObject={mockStartEventBusinessObject}
        nodeName={mockStartEventBusinessObject.name}
        isBold={false}
        isSelected={false}
        hasTopBorder={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByText('flow-node-event-start.svg')).toBeInTheDocument();
    expect(
      screen.getByText(mockStartEventBusinessObject.name)
    ).toBeInTheDocument();
  });

  it('should toggle the timestamp', async () => {
    render(
      <Bar
        flowNodeInstance={mockStartNode}
        businessObject={mockStartEventBusinessObject}
        nodeName={mockStartEventBusinessObject.name}
        isBold={false}
        isSelected={false}
        hasTopBorder={false}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();

    flowNodeTimeStampStore.toggleTimeStampVisibility();

    expect(await screen.findByText(MOCK_TIMESTAMP)).toBeInTheDocument();
  });
});
