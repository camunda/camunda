/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import * as React from 'react';
import {
  TableBatchActions as CarbonTableBatchActions,
  TableBatchAction as CarbonTableBatchAction,
} from './table-batch-actions.carbon';
import {
  TableBatchActions as AdapterTableBatchActions,
  TableBatchAction as AdapterTableBatchAction,
} from './table-batch-actions.adapter';
import {
  TableBatchActions,
  TableBatchAction,
} from './table-batch-actions.shadcn';
import {TrashIcon, DownloadIcon} from 'lucide-react';

const meta: Meta = {title: 'UI/TableBatchActions'};
export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => {
    const [show, setShow] = React.useState(true);
    return (
      <div className="grid grid-cols-1 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">
            Carbon (3 of 8 selected)
          </div>
          <CarbonTableBatchActions
            shouldShowBatchActions
            totalSelected={3}
            totalCount={8}
            onCancel={() => {}}
            onSelectAll={() => {}}
          >
            <CarbonTableBatchAction renderIcon={DownloadIcon}>
              Download
            </CarbonTableBatchAction>
            <CarbonTableBatchAction renderIcon={TrashIcon}>
              Delete
            </CarbonTableBatchAction>
          </CarbonTableBatchActions>
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">
            shadcn ({show ? 'visible' : 'hidden'})
          </div>
          <button
            type="button"
            className="mb-2 text-xs underline"
            onClick={() => setShow((s) => !s)}
          >
            Toggle
          </button>
          <TableBatchActions
            shouldShowBatchActions={show}
            totalSelected={3}
            totalCount={8}
            onCancel={() => setShow(false)}
            onSelectAll={() => {}}
          >
            <TableBatchAction renderIcon={DownloadIcon}>
              Download
            </TableBatchAction>
            <TableBatchAction renderIcon={TrashIcon}>Delete</TableBatchAction>
          </TableBatchActions>
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
          <AdapterTableBatchActions
            shouldShowBatchActions
            totalSelected={3}
            totalCount={8}
            onCancel={() => {}}
            onSelectAll={() => {}}
          >
            <AdapterTableBatchAction renderIcon={DownloadIcon}>
              Download
            </AdapterTableBatchAction>
            <AdapterTableBatchAction renderIcon={TrashIcon}>
              Delete
            </AdapterTableBatchAction>
          </AdapterTableBatchActions>
        </div>
      </div>
    );
  },
};
