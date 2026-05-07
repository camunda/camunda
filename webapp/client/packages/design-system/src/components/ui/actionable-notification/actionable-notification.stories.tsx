/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {AlertTriangle, Info} from 'lucide-react';
import {Button} from '../button/button.shadcn';
import {ActionableNotification as AdapterActionableNotification} from './actionable-notification.adapter';
import {ActionableNotification as CarbonActionableNotification} from './actionable-notification.carbon';
import {
  Alert,
  AlertDescription,
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
  AlertTitle,
} from './actionable-notification.shadcn';

const meta: Meta = {
  title: 'UI/ActionableNotification',
};
export default meta;

type Story = StoryObj;

export const InlineWithCTA: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonActionableNotification
          kind="warning"
          title="Unsaved changes"
          subtitle="You have unsaved changes that will be lost."
          actionButtonLabel="Save now"
          inline
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (compose Alert + Button)
        </div>
        <Alert className="flex items-start gap-4">
          <AlertTriangle className="h-4 w-4" />
          <div className="flex-1">
            <AlertTitle>Unsaved changes</AlertTitle>
            <AlertDescription>
              You have unsaved changes that will be lost.
            </AlertDescription>
          </div>
          <Button size="sm" className="shrink-0">
            Save now
          </Button>
        </Alert>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterActionableNotification
          kind="warning"
          title="Unsaved changes"
          subtitle="You have unsaved changes that will be lost."
          actionButtonLabel="Save now"
          inline
        />
      </div>
    </div>
  ),
};

export const Destructive: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonActionableNotification
          kind="error"
          title="Deletion failed"
          subtitle="The record could not be deleted. Try again."
          actionButtonLabel="Retry"
          inline
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Alert variant="destructive" className="flex items-start gap-4">
          <AlertTriangle className="h-4 w-4" />
          <div className="flex-1">
            <AlertTitle>Deletion failed</AlertTitle>
            <AlertDescription>
              The record could not be deleted. Try again.
            </AlertDescription>
          </div>
          <Button variant="destructive" size="sm" className="shrink-0">
            Retry
          </Button>
        </Alert>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterActionableNotification
          kind="error"
          title="Deletion failed"
          subtitle="The record could not be deleted. Try again."
          actionButtonLabel="Retry"
          inline
        />
      </div>
    </div>
  ),
};

export const Informational: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonActionableNotification
          kind="info"
          title="New version available"
          subtitle="A newer version of this app is available."
          actionButtonLabel="Reload"
          inline
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Alert className="flex items-start gap-4">
          <Info className="h-4 w-4" />
          <div className="flex-1">
            <AlertTitle>New version available</AlertTitle>
            <AlertDescription>
              A newer version of this app is available.
            </AlertDescription>
          </div>
          <Button variant="outline" size="sm" className="shrink-0">
            Reload
          </Button>
        </Alert>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterActionableNotification
          kind="info"
          title="New version available"
          subtitle="A newer version of this app is available."
          actionButtonLabel="Reload"
          inline
        />
      </div>
    </div>
  ),
};

export const ModalConfirmation: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (no modal-actionable variant — use <code>Modal</code> instead)
        </div>
        <CarbonActionableNotification
          kind="warning"
          title="Confirm deletion"
          subtitle="Are you sure? This action cannot be undone."
          actionButtonLabel="Delete"
          inline
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (<code>AlertDialog</code> for blocking confirmations)
        </div>
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button variant="destructive">Delete record</Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Are you sure?</AlertDialogTitle>
              <AlertDialogDescription>
                This action cannot be undone. The record will be permanently
                removed.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction>Delete</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterActionableNotification
          kind="warning"
          title="Confirm deletion"
          subtitle="Are you sure? This action cannot be undone."
          actionButtonLabel="Delete"
          inline
        />
      </div>
    </div>
  ),
};
