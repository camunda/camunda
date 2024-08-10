/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
