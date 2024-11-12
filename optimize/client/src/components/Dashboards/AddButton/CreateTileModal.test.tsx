/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';
import {useLocation} from 'react-router-dom';
import {ComboBox} from '@carbon/react';

import {Tabs} from 'components';
import {loadReports} from 'services';

import CreateTileModal from './CreateTileModal';

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useLocation: jest.fn().mockReturnValue({pathname: '/dashboard/1'}),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadReports: jest.fn().mockReturnValue([]),
  };
});

const props = {
  close: jest.fn(),
  confirm: jest.fn(),
};

it('should load the available reports', () => {
  shallow(<CreateTileModal {...props} />);

  runAllEffects();

  expect(loadReports).toHaveBeenCalled();
});

it('should load only reports in the same collection', () => {
  (useLocation as jest.Mock).mockReturnValueOnce({
    pathname: '/collection/123/dashboard/1',
  });
  shallow(<CreateTileModal {...props} />);

  runAllEffects();

  expect(loadReports).toHaveBeenCalledWith('123');
});

it('should render a Combobox element with the available reports as options', async () => {
  const reports = [
    {
      id: 'a',
      name: 'Report A',
    },
    {
      id: 'b',
      name: 'Report B',
    },
  ];
  (loadReports as jest.Mock).mockReturnValueOnce(reports);
  const node = shallow(<CreateTileModal {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node.find(ComboBox).prop('items')).toEqual([
    {id: 'newReport', name: '+ New report from a template'},
    ...reports,
  ]);
});

it('should call the callback when adding a report', async () => {
  (loadReports as jest.Mock).mockReturnValueOnce([
    {
      id: 'a',
      name: 'Report A',
    },
    {
      id: 'b',
      name: 'Report B',
    },
  ]);
  const spy = jest.fn();
  const node = shallow(<CreateTileModal {...props} confirm={spy} />);

  runAllEffects();
  await flushPromises();

  node.find(ComboBox).prop('onChange')({selectedItem: {id: 'a'}});

  node.find('Button').at(1).simulate('click');

  expect(spy).toHaveBeenCalledWith({
    id: 'a',
    type: 'optimize_report',
  });
});

it('should show a loading skeleton while loading available reports', () => {
  const node = shallow(<CreateTileModal {...props} />);

  expect(node.find('TextInputSkeleton')).toExist();
});

it('should contain an External Website field', () => {
  const node = shallow(<CreateTileModal {...props} />);

  expect(node.find(Tabs.Tab).at(1).prop('title')).toBe('External website');
});

it('should hide the typeahead when external mode is enabled', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'external');

  expect(node.find('Typeahead')).not.toExist();
});

it('should contain a text input field if in external source mode', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'external_url');

  expect(node.find('.externalInput')).toExist();
});

it('should  disable the submit button if the url does not start with http in external mode', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'external_url');
  node.find('.externalInput').simulate('change', {
    target: {value: 'Dear computer, please show me a report. Thanks.'},
  });

  expect(node.find('Button').at(1)).toBeDisabled();
});

it('should contain an Text field', () => {
  const node = shallow(<CreateTileModal {...props} />);

  expect(node.find(Tabs.Tab).at(2).prop('title')).toBe('Text');
});

it('should contain text editor if in text report mode', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'text');

  expect(node.find('TextEditor')).toExist();
});

it('should  disable the submit button if the text in editor is empty or too long', () => {
  const node = shallow(<CreateTileModal {...props} />);

  node.find(Tabs).simulate('change', 'text');

  expect(node.find('Button').at(1)).toBeDisabled();

  const normalText = {
    root: {
      children: [
        {
          children: [
            {
              text: 'a'.repeat(10),
            },
          ],
          type: 'paragraph',
        },
      ],
      type: 'root',
    },
  };
  node.find('TextEditor').simulate('change', normalText);
  expect(node.find('Button').at(1)).not.toBeDisabled();

  const tooLongText = {
    root: {
      children: [
        {
          children: [
            {
              text: 'a'.repeat(3001),
            },
          ],
          type: 'paragraph',
        },
      ],
      type: 'root',
    },
  };

  node.find('TextEditor').simulate('change', tooLongText);

  expect(node.find('Button').at(1)).toBeDisabled();
});
