/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  Tab as AdapterTab,
  TabList as AdapterTabList,
  TabPanel as AdapterTabPanel,
  TabPanels as AdapterTabPanels,
  Tabs as AdapterTabs,
} from './tabs.adapter';
import {
  Tab as CarbonTab,
  TabList as CarbonTabList,
  TabPanel as CarbonTabPanel,
  TabPanels as CarbonTabPanels,
  Tabs as CarbonTabs,
} from './tabs.carbon';
import {Tabs, TabsContent, TabsList, TabsTrigger} from './tabs.shadcn';

const meta: Meta = {
  title: 'UI/Tabs',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTabs>
          <CarbonTabList aria-label="Carbon tabs">
            <CarbonTab>Account</CarbonTab>
            <CarbonTab>Password</CarbonTab>
            <CarbonTab>Notifications</CarbonTab>
          </CarbonTabList>
          <CarbonTabPanels>
            <CarbonTabPanel>
              <p className="text-sm">Update your profile and account info.</p>
            </CarbonTabPanel>
            <CarbonTabPanel>
              <p className="text-sm">Change your password here.</p>
            </CarbonTabPanel>
            <CarbonTabPanel>
              <p className="text-sm">Manage notification preferences.</p>
            </CarbonTabPanel>
          </CarbonTabPanels>
        </CarbonTabs>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Tabs defaultValue="account">
          <TabsList>
            <TabsTrigger value="account">Account</TabsTrigger>
            <TabsTrigger value="password">Password</TabsTrigger>
            <TabsTrigger value="notifications">Notifications</TabsTrigger>
          </TabsList>
          <TabsContent value="account">
            <p className="text-sm">Update your profile and account info.</p>
          </TabsContent>
          <TabsContent value="password">
            <p className="text-sm">Change your password here.</p>
          </TabsContent>
          <TabsContent value="notifications">
            <p className="text-sm">Manage notification preferences.</p>
          </TabsContent>
        </Tabs>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTabs>
          <AdapterTabList aria-label="Adapter tabs">
            <AdapterTab>Account</AdapterTab>
            <AdapterTab>Password</AdapterTab>
            <AdapterTab>Notifications</AdapterTab>
          </AdapterTabList>
          <AdapterTabPanels>
            <AdapterTabPanel>
              <p className="text-sm">Update your profile and account info.</p>
            </AdapterTabPanel>
            <AdapterTabPanel>
              <p className="text-sm">Change your password here.</p>
            </AdapterTabPanel>
            <AdapterTabPanel>
              <p className="text-sm">Manage notification preferences.</p>
            </AdapterTabPanel>
          </AdapterTabPanels>
        </AdapterTabs>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTabs>
          <CarbonTabList aria-label="Carbon tabs disabled">
            <CarbonTab>Available</CarbonTab>
            <CarbonTab disabled>Disabled</CarbonTab>
            <CarbonTab>Other</CarbonTab>
          </CarbonTabList>
          <CarbonTabPanels>
            <CarbonTabPanel>
              <p className="text-sm">First panel.</p>
            </CarbonTabPanel>
            <CarbonTabPanel>
              <p className="text-sm">This panel cannot be reached.</p>
            </CarbonTabPanel>
            <CarbonTabPanel>
              <p className="text-sm">Third panel.</p>
            </CarbonTabPanel>
          </CarbonTabPanels>
        </CarbonTabs>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Tabs defaultValue="available">
          <TabsList>
            <TabsTrigger value="available">Available</TabsTrigger>
            <TabsTrigger value="disabled" disabled>
              Disabled
            </TabsTrigger>
            <TabsTrigger value="other">Other</TabsTrigger>
          </TabsList>
          <TabsContent value="available">
            <p className="text-sm">First panel.</p>
          </TabsContent>
          <TabsContent value="disabled">
            <p className="text-sm">This panel cannot be reached.</p>
          </TabsContent>
          <TabsContent value="other">
            <p className="text-sm">Third panel.</p>
          </TabsContent>
        </Tabs>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTabs>
          <AdapterTabList aria-label="Adapter tabs disabled">
            <AdapterTab>Available</AdapterTab>
            <AdapterTab disabled>Disabled</AdapterTab>
            <AdapterTab>Other</AdapterTab>
          </AdapterTabList>
          <AdapterTabPanels>
            <AdapterTabPanel>
              <p className="text-sm">First panel.</p>
            </AdapterTabPanel>
            <AdapterTabPanel>
              <p className="text-sm">This panel cannot be reached.</p>
            </AdapterTabPanel>
            <AdapterTabPanel>
              <p className="text-sm">Third panel.</p>
            </AdapterTabPanel>
          </AdapterTabPanels>
        </AdapterTabs>
      </div>
    </div>
  ),
};

export const ContainedVariant: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (<code>contained</code>)
        </div>
        <CarbonTabs>
          <CarbonTabList aria-label="Carbon contained tabs" contained>
            <CarbonTab>Overview</CarbonTab>
            <CarbonTab>Activity</CarbonTab>
            <CarbonTab>Members</CarbonTab>
          </CarbonTabList>
          <CarbonTabPanels>
            <CarbonTabPanel>
              <p className="text-sm">Overview content.</p>
            </CarbonTabPanel>
            <CarbonTabPanel>
              <p className="text-sm">Activity feed.</p>
            </CarbonTabPanel>
            <CarbonTabPanel>
              <p className="text-sm">Member list.</p>
            </CarbonTabPanel>
          </CarbonTabPanels>
        </CarbonTabs>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (default visual is the contained / pill style)
        </div>
        <Tabs defaultValue="overview">
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="activity">Activity</TabsTrigger>
            <TabsTrigger value="members">Members</TabsTrigger>
          </TabsList>
          <TabsContent value="overview">
            <p className="text-sm">Overview content.</p>
          </TabsContent>
          <TabsContent value="activity">
            <p className="text-sm">Activity feed.</p>
          </TabsContent>
          <TabsContent value="members">
            <p className="text-sm">Member list.</p>
          </TabsContent>
        </Tabs>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, contained)
        </div>
        <AdapterTabs>
          <AdapterTabList aria-label="Adapter contained tabs" contained>
            <AdapterTab>Overview</AdapterTab>
            <AdapterTab>Activity</AdapterTab>
            <AdapterTab>Members</AdapterTab>
          </AdapterTabList>
          <AdapterTabPanels>
            <AdapterTabPanel>
              <p className="text-sm">Overview content.</p>
            </AdapterTabPanel>
            <AdapterTabPanel>
              <p className="text-sm">Activity feed.</p>
            </AdapterTabPanel>
            <AdapterTabPanel>
              <p className="text-sm">Member list.</p>
            </AdapterTabPanel>
          </AdapterTabPanels>
        </AdapterTabs>
      </div>
    </div>
  ),
};
