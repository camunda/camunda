/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {xTimes, createSelection} from 'modules/testUtils';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {DataManagerProvider} from 'modules/DataManager';

import AddSelection from './AddSelection';
jest.mock('modules/utils/bpmn');

const maxSelections = [];
xTimes(10)(index => maxSelections.push(createSelection(index)));

const mockFunctions = {
  onAddNewSelection: jest.fn(),
  onAddToSelectionById: jest.fn(),
  onAddToOpenSelection: jest.fn(),
  expandSelections: jest.fn()
};

describe('AddSelection', () => {
  afterEach(() => {
    Object.values(mockFunctions).forEach(mockFunction =>
      mockFunction.mockClear()
    );
  });

  describe('Create selection', () => {
    it('should be disabled if no instance is selected', () => {
      // given
      const node = mount(
        <DataManagerProvider>
          <ThemeProvider>
            <AddSelection
              {...mockFunctions}
              selections={[]}
              selectedInstances={{ids: [], excludeIds: []}}
            />
          </ThemeProvider>
        </DataManagerProvider>
      );
      const selectionButton = node.find('button');

      // then
      expect(selectionButton.contains('Create Selection')).toBe(true);
      expect(selectionButton.prop('disabled')).toBe(true);
    });

    it('should create new selection', () => {
      // given
      const node = mount(
        <DataManagerProvider>
          <ThemeProvider>
            <AddSelection
              {...mockFunctions}
              selections={[]}
              selectedInstances={{all: true}}
            />
          </ThemeProvider>
        </DataManagerProvider>
      );
      const selectionButton = node.find('button');

      // when
      selectionButton.simulate('click');

      // then
      expect(mockFunctions.onAddNewSelection).toBeCalled();
      expect(mockFunctions.expandSelections).toBeCalled();
    });
  });

  describe('DropdownMenu', () => {
    it('should be disabled if no instance is selected', () => {
      // given
      const node = mount(
        <DataManagerProvider>
          <ThemeProvider>
            <AddSelection
              {...mockFunctions}
              selections={[{selectionId: 1}, {selectionId: 2}]}
              selectedInstances={{ids: [], excludeIds: []}}
            />
          </ThemeProvider>
        </DataManagerProvider>
      );
      const dropdownButton = node.find('button');

      // then
      expect(dropdownButton.prop('disabled')).toBe(true);
    });

    it('should drop "up"', () => {
      // given
      const node = mount(
        <DataManagerProvider>
          <ThemeProvider>
            <AddSelection
              {...mockFunctions}
              selections={[{selectionId: 1}, {selectionId: 2}]}
              selectedInstances={{all: true}}
            />
          </ThemeProvider>
        </DataManagerProvider>
      );
      const dropdownButton = node.find('button');

      // when
      dropdownButton.simulate('click');

      // then
      const menuItems = node.find('[data-test="menu"] li button');
      // create new selection
      expect(menuItems.at(0).contains('Create New Selection')).toBe(true);
      expect(menuItems.at(1).contains('Add to current Selection')).toBe(true);
      expect(menuItems.at(1).prop('disabled')).toBe(true);
      expect(menuItems.at(2).contains('Add to Selection...')).toBe(true);
    });

    it('should create new selection', () => {
      // given
      const node = mount(
        <DataManagerProvider>
          <ThemeProvider>
            <AddSelection
              {...mockFunctions}
              selections={[{selectionId: 1}, {selectionId: 2}]}
              selectedInstances={{all: true}}
            />
          </ThemeProvider>
        </DataManagerProvider>
      );
      const dropdownButton = node.find('button');
      dropdownButton.simulate('click');
      const createButton = node.find('[data-test="menu"] li button').at(0);

      // when
      createButton.simulate('click');

      // then
      expect(mockFunctions.onAddNewSelection).toBeCalled();
      expect(mockFunctions.expandSelections).toBeCalled();
    });

    it('should add to open selection', () => {
      // given
      const node = mount(
        <DataManagerProvider>
          <ThemeProvider>
            <AddSelection
              {...mockFunctions}
              selections={[{selectionId: 1}, {selectionId: 2}]}
              selectedInstances={{all: true}}
              openSelection={1}
            />
          </ThemeProvider>
        </DataManagerProvider>
      );
      const dropdownButton = node.find('button');
      dropdownButton.simulate('click');
      const addButton = node.find('[data-test="menu"] li button').at(1);

      // when
      addButton.simulate('click');

      // then
      expect(addButton.prop('disabled')).toBe(false);
      expect(mockFunctions.onAddToOpenSelection).toBeCalled();
    });

    describe('Add to selection', () => {
      it('should show a submenu on hover', () => {
        // given
        const node = mount(
          <DataManagerProvider>
            <ThemeProvider>
              <AddSelection
                {...mockFunctions}
                selections={[{selectionId: 1}, {selectionId: 2}]}
                selectedInstances={{all: true}}
              />
            </ThemeProvider>
          </DataManagerProvider>
        );
        const dropdownButton = node.find('button');
        dropdownButton.simulate('click');
        const addToSelectionButton = node
          .find('[data-test="menu"] li button')
          .at(2);

        // when
        addToSelectionButton.simulate('mouseover');

        // then
        const submenuButtons = node.find('[data-test="sub-menu"] li button');
        expect(submenuButtons.at(0).contains(1)).toBe(true);
        expect(submenuButtons.at(1).contains(2)).toBe(true);
      });

      it('should add to selected selection', () => {
        // given
        const node = mount(
          <DataManagerProvider>
            <ThemeProvider>
              <AddSelection
                {...mockFunctions}
                selections={[{selectionId: 1}, {selectionId: 2}]}
                selectedInstances={{all: true}}
              />
            </ThemeProvider>
          </DataManagerProvider>
        );
        const dropdownButton = node.find('button');
        dropdownButton.simulate('click');
        const addToSelectionButton = node
          .find('[data-test="menu"] li button')
          .at(2);
        addToSelectionButton.simulate('mouseover');
        const firstSubMenuButton = node
          .find('[data-test="sub-menu"] li button')
          .at(0);

        // when
        firstSubMenuButton.simulate('click');

        // then
        expect(mockFunctions.onAddToSelectionById).toBeCalledWith(1);
      });
    });
  });
});
