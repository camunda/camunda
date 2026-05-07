/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {AlertTriangle, CheckCircle2, Info, X, XCircle} from 'lucide-react';
import * as React from 'react';
import {Button} from '../button/button.shadcn';
import {InlineNotification as AdapterInlineNotification} from './inline-notification.adapter';
import {InlineNotification as CarbonInlineNotification} from './inline-notification.carbon';
import {Alert, AlertDescription, AlertTitle} from './inline-notification.shadcn';

const meta: Meta = {
  title: 'UI/InlineNotification',
};
export default meta;

type Story = StoryObj;

const Dismissible = ({
  variant = 'default',
  borderClass,
  iconColorClass,
  Icon,
  title,
  subtitle,
}: {
  variant?: 'default' | 'destructive';
  borderClass?: string;
  iconColorClass?: string;
  Icon: React.ComponentType<{className?: string}>;
  title: string;
  subtitle: string;
}) => {
  const [open, setOpen] = React.useState(true);
  if (!open) return null;
  return (
    <Alert
      variant={variant}
      className={`flex items-start gap-3 pr-2 ${borderClass ?? ''}`}
    >
      <Icon className={`h-4 w-4 ${iconColorClass ?? ''}`} />
      <div className="flex-1">
        <AlertTitle>{title}</AlertTitle>
        <AlertDescription>{subtitle}</AlertDescription>
      </div>
      <Button
        variant="ghost"
        size="icon"
        aria-label="Dismiss"
        className="shrink-0 h-6 w-6"
        onClick={() => setOpen(false)}
      >
        <X className="h-3 w-3" />
      </Button>
    </Alert>
  );
};

export const ErrorKind: Story = {
  name: 'Error',
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineNotification
          kind="error"
          title="Something went wrong"
          subtitle="The record could not be saved. Try again."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (with dismiss)</div>
        <Dismissible
          variant="destructive"
          Icon={XCircle}
          title="Something went wrong"
          subtitle="The record could not be saved. Try again."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterInlineNotification
          kind="error"
          title="Something went wrong"
          subtitle="The record could not be saved. Try again."
        />
      </div>
    </div>
  ),
};

export const InfoKind: Story = {
  name: 'Info',
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineNotification
          kind="info"
          title="New version available"
          subtitle="Reload the page to see the latest content."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Dismissible
          Icon={Info}
          title="New version available"
          subtitle="Reload the page to see the latest content."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterInlineNotification
          kind="info"
          title="New version available"
          subtitle="Reload the page to see the latest content."
        />
      </div>
    </div>
  ),
};

export const SuccessKind: Story = {
  name: 'Success',
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineNotification
          kind="success"
          title="Saved"
          subtitle="Your changes have been saved successfully."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (no success variant — restyle via className)
        </div>
        <Dismissible
          Icon={CheckCircle2}
          borderClass="border-green-500/50 text-green-700 dark:border-green-500"
          iconColorClass="text-green-600"
          title="Saved"
          subtitle="Your changes have been saved successfully."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterInlineNotification
          kind="success"
          title="Saved"
          subtitle="Your changes have been saved successfully."
        />
      </div>
    </div>
  ),
};

export const WarningKind: Story = {
  name: 'Warning',
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineNotification
          kind="warning"
          title="Read carefully"
          subtitle="Some changes here cannot be undone."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (no warning variant — restyle via className)
        </div>
        <Dismissible
          Icon={AlertTriangle}
          borderClass="border-yellow-500/50 text-yellow-700 dark:border-yellow-500"
          iconColorClass="text-yellow-600"
          title="Read carefully"
          subtitle="Some changes here cannot be undone."
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterInlineNotification
          kind="warning"
          title="Read carefully"
          subtitle="Some changes here cannot be undone."
        />
      </div>
    </div>
  ),
};

export const NoCloseButton: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonInlineNotification
          kind="info"
          title="System notice"
          subtitle="This message cannot be dismissed."
          hideCloseButton
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (just don't render the close button)
        </div>
        <Alert>
          <Info className="h-4 w-4" />
          <AlertTitle>System notice</AlertTitle>
          <AlertDescription>This message cannot be dismissed.</AlertDescription>
        </Alert>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterInlineNotification
          kind="info"
          title="System notice"
          subtitle="This message cannot be dismissed."
          hideCloseButton
        />
      </div>
    </div>
  ),
};
