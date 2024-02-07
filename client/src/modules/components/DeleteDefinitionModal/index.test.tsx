/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {DeleteDefinitionModal} from './index';
import {render, screen} from 'modules/testing-library';

describe('<DeleteDefinitionModal />', () => {
  it('should start the modal with an unchecked checkbox', () => {
    render(
      <DeleteDefinitionModal
        isVisible={true}
        title="warning"
        description="description"
        bodyContent={<div>body</div>}
        confirmationText="confirm"
        onClose={() => {}}
        onDelete={() => {}}
      />,
    );

    expect(screen.queryByRole('checkbox')).not.toBeChecked();
  });
});
