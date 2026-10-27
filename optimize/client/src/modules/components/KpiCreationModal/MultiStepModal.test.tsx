/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps} from 'react';
import {shallow} from 'enzyme';

import {Button, ProgressStep} from '@carbon/react';

import {MultiStepModal} from './MultiStepModal';

const props: ComponentProps<typeof MultiStepModal> = {
  title: 'testModal',
  steps: [
    {
      title: 'Step 1',
      subtitle: 'Subtitle 1',
      content: <div>Step 1 Content</div>,
      actions: {
        primary: {
          label: 'Next',
          kind: 'primary',
          onClick: jest.fn(),
        },
        secondary: {
          label: 'Cancel',
          kind: 'secondary',
          onClick: jest.fn(),
        },
      },
    },
    {
      title: 'Step 2',
      subtitle: 'Subtitle 2',
      content: <div>Step 2 Content</div>,
      actions: {
        primary: {
          label: 'Finish',
          kind: 'primary',
          onClick: jest.fn(),
        },
        secondary: {
          label: 'Back',
          kind: 'secondary',
          onClick: jest.fn(),
        },
      },
    },
  ],
  onClose: jest.fn(),
};

it('should display the correct step labels in ProgressIndicator', () => {
  const node = shallow(<MultiStepModal {...props} />);
  const progressSteps = node.find(ProgressStep);
  expect(progressSteps).toHaveLength(props.steps.length);
  progressSteps.forEach((step, index) => {
    expect(step.prop('label')).toEqual(props.steps[index]?.title);
    expect(step.prop('secondaryLabel')).toEqual(props.steps[index]?.subtitle);
  });
});

it('should call the correct onClick function when primary button is clicked', async () => {
  const node = shallow(<MultiStepModal {...props} />);
  const primaryButton = node.find(Button).find({kind: 'primary'});
  await primaryButton.simulate('click');
  expect(props.steps[0]?.actions.primary.onClick).toHaveBeenCalledWith(0);
});

it('should call the correct onClick function when secondary button is clicked', () => {
  const node = shallow(<MultiStepModal title="Test Modal" {...props} />);
  const secondaryButton = node.find(Button).find({kind: 'secondary'});
  secondaryButton.simulate('click');
  expect(props.steps[0]?.actions.secondary?.onClick).toHaveBeenCalledWith(0);
});
