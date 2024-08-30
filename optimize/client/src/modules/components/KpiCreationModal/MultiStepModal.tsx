/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Button,
  Stack,
  ProgressIndicator,
  ProgressStep,
  ButtonKind,
  InlineLoading,
} from '@carbon/react';
import {t} from 'translation';
import React, {ComponentProps, ReactNode} from 'react';

import {Modal} from 'components';

interface MultiStepModalAction {
  label: ReactNode;
  kind: ButtonKind;
  onClick?: (step?: number) => void | Promise<void>;
  disabled?: boolean;
}

export interface MultiStepModalContent {
  title: string;
  subtitle?: string;
  content: JSX.Element;
  actions: {
    primary: MultiStepModalAction;
    secondary?: MultiStepModalAction;
  };
}

export interface MultiStepModalProps
  extends Pick<ComponentProps<typeof Modal>, 'title' | 'size' | 'className'> {
  steps: MultiStepModalContent[];
  onClose: () => void;
}

export function MultiStepModal({
  title,
  steps,
  size,
  onClose,
  className,
}: MultiStepModalProps): JSX.Element {
  const [step, setStep] = React.useState<number>(0);
  const [loading, setLoading] = React.useState<boolean>(false);

  const {actions, content} = steps[step] || {};
  const {
    primary,
    secondary = {
      label: t('common.cancel'),
      kind: 'secondary',
      onClick: onClose,
      disabled: false,
    },
  } = actions || {};

  return (
    <Modal className={className} size={size} open onClose={onClose} isOverflowVisible>
      <Modal.Header title={title} />
      <Modal.Content>
        <Stack gap={8}>
          <ProgressIndicator currentIndex={step} spaceEqually>
            {steps.map((step, index) => (
              <ProgressStep key={index} label={step.title} secondaryLabel={step.subtitle} />
            ))}
          </ProgressIndicator>
          {content}
        </Stack>
      </Modal.Content>
      <Modal.Footer>
        {secondary && (
          <Button
            kind={secondary.kind}
            disabled={loading || secondary.disabled}
            onClick={() => {
              if (step > 0) {
                setStep(step - 1);
              }
              secondary.onClick?.(step);
            }}
          >
            {secondary.label}
          </Button>
        )}
        {primary && (
          <Button
            kind={primary.kind}
            disabled={loading || primary.disabled}
            onClick={async () => {
              setLoading(true);
              await primary.onClick?.(step);
              setLoading(false);
              if (step < steps.length - 1) {
                setStep(step + 1);
              }
            }}
          >
            {loading ? <InlineLoading description={primary.label} /> : primary.label}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  );
}
