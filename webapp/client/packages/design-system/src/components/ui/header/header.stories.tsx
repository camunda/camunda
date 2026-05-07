/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  Header as CarbonHeader,
  HeaderName as CarbonHeaderName,
  HeaderNavigation as CarbonHeaderNavigation,
  HeaderMenuItem as CarbonHeaderMenuItem,
  HeaderGlobalBar as CarbonHeaderGlobalBar,
  HeaderGlobalAction as CarbonHeaderGlobalAction,
} from '@carbon/react';
import {Bell, Settings, User} from 'lucide-react';
import {Header as AdapterHeader} from './header.adapter';
import {
  Header,
  HeaderName,
  HeaderNavigation,
  HeaderMenuItem,
  HeaderGlobalBar,
  HeaderGlobalAction,
} from './header.shadcn';

const meta: Meta = {
  title: 'UI/Header',
};
export default meta;

type Story = StoryObj;

export const Bare: Story = {
  render: () => (
    <div className="flex flex-col gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonHeader aria-label="Camunda Tasklist">
          <CarbonHeaderName href="#" prefix="Camunda">
            Tasklist
          </CarbonHeaderName>
        </CarbonHeader>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Header aria-label="Camunda Tasklist">
          <HeaderName href="#" prefix="Camunda">
            Tasklist
          </HeaderName>
        </Header>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterHeader aria-label="Camunda Tasklist">
          <HeaderName href="#" prefix="Camunda">
            Tasklist
          </HeaderName>
        </AdapterHeader>
      </div>
    </div>
  ),
};

export const FullHeader: Story = {
  render: () => (
    <div className="flex flex-col gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonHeader aria-label="Camunda Operate">
          <CarbonHeaderName href="#" prefix="Camunda">
            Operate
          </CarbonHeaderName>
          <CarbonHeaderNavigation aria-label="Operate sections">
            <CarbonHeaderMenuItem href="#" isActive>
              Dashboard
            </CarbonHeaderMenuItem>
            <CarbonHeaderMenuItem href="#">Processes</CarbonHeaderMenuItem>
            <CarbonHeaderMenuItem href="#">Decisions</CarbonHeaderMenuItem>
          </CarbonHeaderNavigation>
          <CarbonHeaderGlobalBar>
            <CarbonHeaderGlobalAction aria-label="Notifications">
              <Bell />
            </CarbonHeaderGlobalAction>
            <CarbonHeaderGlobalAction aria-label="Settings">
              <Settings />
            </CarbonHeaderGlobalAction>
            <CarbonHeaderGlobalAction aria-label="Account">
              <User />
            </CarbonHeaderGlobalAction>
          </CarbonHeaderGlobalBar>
        </CarbonHeader>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Header aria-label="Camunda Operate">
          <HeaderName href="#" prefix="Camunda">
            Operate
          </HeaderName>
          <HeaderNavigation aria-label="Operate sections">
            <HeaderMenuItem href="#" isActive>
              Dashboard
            </HeaderMenuItem>
            <HeaderMenuItem href="#">Processes</HeaderMenuItem>
            <HeaderMenuItem href="#">Decisions</HeaderMenuItem>
          </HeaderNavigation>
          <HeaderGlobalBar>
            <HeaderGlobalAction aria-label="Notifications">
              <Bell />
            </HeaderGlobalAction>
            <HeaderGlobalAction aria-label="Settings">
              <Settings />
            </HeaderGlobalAction>
            <HeaderGlobalAction aria-label="Account">
              <User />
            </HeaderGlobalAction>
          </HeaderGlobalBar>
        </Header>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterHeader aria-label="Camunda Operate">
          <HeaderName href="#" prefix="Camunda">
            Operate
          </HeaderName>
          <HeaderNavigation aria-label="Operate sections">
            <HeaderMenuItem href="#" isActive>
              Dashboard
            </HeaderMenuItem>
            <HeaderMenuItem href="#">Processes</HeaderMenuItem>
            <HeaderMenuItem href="#">Decisions</HeaderMenuItem>
          </HeaderNavigation>
          <HeaderGlobalBar>
            <HeaderGlobalAction aria-label="Notifications">
              <Bell />
            </HeaderGlobalAction>
            <HeaderGlobalAction aria-label="Settings">
              <Settings />
            </HeaderGlobalAction>
            <HeaderGlobalAction aria-label="Account">
              <User />
            </HeaderGlobalAction>
          </HeaderGlobalBar>
        </AdapterHeader>
      </div>
    </div>
  ),
};
