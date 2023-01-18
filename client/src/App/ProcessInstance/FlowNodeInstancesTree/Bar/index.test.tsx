/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {act, render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Bar} from './index';
import {mockStartNode, mockStartEventBusinessObject} from './index.setup';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {useEffect} from 'react';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => flowNodeTimeStampStore.reset, []);

  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('<Bar />', () => {
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
        wrapper: Wrapper,
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
        wrapper: Wrapper,
      }
    );

    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();

    act(() => {
      flowNodeTimeStampStore.toggleTimeStampVisibility();
    });

    expect(await screen.findByText(MOCK_TIMESTAMP)).toBeInTheDocument();
  });
});
