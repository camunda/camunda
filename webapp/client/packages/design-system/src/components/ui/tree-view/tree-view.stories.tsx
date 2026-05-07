/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {TreeView as CarbonTreeView, TreeNode as CarbonTreeNode} from '@carbon/react';
import {Folder, FileText} from 'lucide-react';
import {
  TreeNode as AdapterTreeNode,
  TreeView as AdapterTreeView,
} from './tree-view.adapter';
import {TreeView, TreeNode} from './tree-view.shadcn';

const meta: Meta = {
  title: 'UI/TreeView',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTreeView label="File system">
          <CarbonTreeNode id="src" label="src" defaultIsExpanded>
            <CarbonTreeNode id="components" label="components" defaultIsExpanded>
              <CarbonTreeNode id="Button" label="Button.tsx" />
              <CarbonTreeNode id="Card" label="Card.tsx" />
            </CarbonTreeNode>
            <CarbonTreeNode id="utils" label="utils" />
          </CarbonTreeNode>
          <CarbonTreeNode id="package" label="package.json" />
        </CarbonTreeView>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <TreeView label="File system">
          <TreeNode id="src" label="src" defaultIsExpanded renderIcon={Folder}>
            <TreeNode
              id="components"
              label="components"
              defaultIsExpanded
              renderIcon={Folder}
            >
              <TreeNode id="Button" label="Button.tsx" renderIcon={FileText} />
              <TreeNode id="Card" label="Card.tsx" renderIcon={FileText} />
            </TreeNode>
            <TreeNode id="utils" label="utils" renderIcon={Folder} />
          </TreeNode>
          <TreeNode id="package" label="package.json" renderIcon={FileText} />
        </TreeView>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTreeView label="File system">
          <AdapterTreeNode id="src" label="src" defaultIsExpanded>
            <AdapterTreeNode
              id="components"
              label="components"
              defaultIsExpanded
            >
              <AdapterTreeNode id="Button" label="Button.tsx" />
              <AdapterTreeNode id="Card" label="Card.tsx" />
            </AdapterTreeNode>
            <AdapterTreeNode id="utils" label="utils" />
          </AdapterTreeNode>
          <AdapterTreeNode id="package" label="package.json" />
        </AdapterTreeView>
      </div>
    </div>
  ),
};

export const SizeXS: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (size=xs)</div>
        <CarbonTreeView label="Hierarchy" size="xs">
          <CarbonTreeNode id="a" label="Item A" defaultIsExpanded>
            <CarbonTreeNode id="a1" label="Item A.1" />
            <CarbonTreeNode id="a2" label="Item A.2" />
          </CarbonTreeNode>
          <CarbonTreeNode id="b" label="Item B" />
        </CarbonTreeView>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (size=xs)</div>
        <TreeView label="Hierarchy" size="xs">
          <TreeNode id="a" label="Item A" defaultIsExpanded>
            <TreeNode id="a1" label="Item A.1" />
            <TreeNode id="a2" label="Item A.2" />
          </TreeNode>
          <TreeNode id="b" label="Item B" />
        </TreeView>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, size=xs)
        </div>
        <AdapterTreeView label="Hierarchy" size="xs">
          <AdapterTreeNode id="a" label="Item A" defaultIsExpanded>
            <AdapterTreeNode id="a1" label="Item A.1" />
            <AdapterTreeNode id="a2" label="Item A.2" />
          </AdapterTreeNode>
          <AdapterTreeNode id="b" label="Item B" />
        </AdapterTreeView>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTreeView label="Tasks">
          <CarbonTreeNode id="t1" label="Open task" />
          <CarbonTreeNode id="t2" label="Locked task" disabled />
        </CarbonTreeView>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <TreeView label="Tasks">
          <TreeNode id="t1" label="Open task" />
          <TreeNode id="t2" label="Locked task" disabled />
        </TreeView>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTreeView label="Tasks">
          <AdapterTreeNode id="t1" label="Open task" />
          <AdapterTreeNode id="t2" label="Locked task" disabled />
        </AdapterTreeView>
      </div>
    </div>
  ),
};

export const HiddenLabel: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (hideLabel)</div>
        <CarbonTreeView label="navigation" hideLabel>
          <CarbonTreeNode id="x" label="One" />
          <CarbonTreeNode id="y" label="Two" />
        </CarbonTreeView>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (hideLabel)</div>
        <TreeView label="navigation" hideLabel>
          <TreeNode id="x" label="One" />
          <TreeNode id="y" label="Two" />
        </TreeView>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, hideLabel)
        </div>
        <AdapterTreeView label="navigation" hideLabel>
          <AdapterTreeNode id="x" label="One" />
          <AdapterTreeNode id="y" label="Two" />
        </AdapterTreeView>
      </div>
    </div>
  ),
};
