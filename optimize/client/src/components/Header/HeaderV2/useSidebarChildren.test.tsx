/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ShallowWrapper, shallow} from 'enzyme';
import {useLocation} from 'react-router-dom';
import type {SidebarGroupItem, SidebarNode} from '@camunda/design-system';

import useSidebarChildren from './useSidebarChildren';

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useLocation: jest.fn().mockReturnValue({pathname: '/'}),
}));

jest.mock('translation', () => ({
  t: jest.fn().mockImplementation((key) => key),
}));

const Sidebar = ({noActions}: {noActions?: boolean}) => {
  const items = useSidebarChildren(noActions);
  return <div data-items={items} />;
};

function getItems(node: ShallowWrapper): SidebarNode[] {
  return (node.props() as {'data-items': SidebarNode[]})['data-items'];
}

function getAnalysis(node: ShallowWrapper): SidebarGroupItem {
  return getItems(node).find((item) => item.key === 'analysis') as SidebarGroupItem;
}

it('should render no navigation when actions are hidden', () => {
  const node = shallow(<Sidebar noActions />);

  expect(getItems(node)).toEqual([]);
});

it('should mark the item matching the current route as active', () => {
  const node = shallow(<Sidebar />);

  const items = getItems(node);
  expect(items.find((item) => item.key === 'dashboards')).toMatchObject({isActive: true});
  expect(items.find((item) => item.key === 'collections')).toMatchObject({isActive: false});
});

it('should not treat an instant dashboard as a collection', () => {
  (useLocation as jest.Mock).mockReturnValueOnce({pathname: '/dashboard/instant/processId'});
  const node = shallow(<Sidebar />);

  const items = getItems(node);
  expect(items.find((item) => item.key === 'dashboards')).toMatchObject({isActive: true});
  expect(items.find((item) => item.key === 'collections')).toMatchObject({isActive: false});
});

it('should expand the analysis group while on an analysis route', () => {
  (useLocation as jest.Mock).mockReturnValue({pathname: '/analysis/branchAnalysis'});
  const node = shallow(<Sidebar />);

  expect(getAnalysis(node)).toMatchObject({isActive: true, isExpanded: true});
});

it('should allow collapsing the analysis group while on an analysis route', () => {
  (useLocation as jest.Mock).mockReturnValue({pathname: '/analysis/branchAnalysis'});
  const node = shallow(<Sidebar />);

  getAnalysis(node).onToggleExpand?.(false);
  node.update();

  expect(getAnalysis(node).isExpanded).toBe(false);
});

it('should collapse the analysis group outside of the analysis routes', () => {
  (useLocation as jest.Mock).mockReturnValue({pathname: '/collections'});
  const node = shallow(<Sidebar />);

  expect(getAnalysis(node)).toMatchObject({isActive: false, isExpanded: false});
});

it('should keep a manual collapse when leaving and returning to the analysis routes', () => {
  (useLocation as jest.Mock).mockReturnValue({pathname: '/analysis/branchAnalysis'});
  const node = shallow(<Sidebar />);

  getAnalysis(node).onToggleExpand?.(false);
  node.update();
  expect(getAnalysis(node).isExpanded).toBe(false);

  (useLocation as jest.Mock).mockReturnValue({pathname: '/collections'});
  node.setProps({});
  expect(getAnalysis(node).isExpanded).toBe(false);

  (useLocation as jest.Mock).mockReturnValue({pathname: '/analysis/branchAnalysis'});
  node.setProps({});

  expect(getAnalysis(node).isExpanded).toBe(false);
});
