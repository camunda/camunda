/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Tabs, Typeahead} from 'components';
import {loadReports} from 'services';

import ReportModal from './ReportModal';

jest.mock('react-router-dom', () => {
  const rest = jest.requireActual('react-router-dom');
  return {
    ...rest,
    withRouter: (a) => a,
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadReports: jest.fn().mockReturnValue([]),
  };
});

const props = {
  location: {pathname: '/dashboard/1'},
};

it('should load the available reports', () => {
  shallow(<ReportModal {...props} />);

  runAllEffects();

  expect(loadReports).toHaveBeenCalled();
});

it('should load only reports in the same collection', () => {
  shallow(<ReportModal location={{pathname: '/collection/123/dashboard/1'}} />);

  runAllEffects();

  expect(loadReports).toHaveBeenCalledWith('123');
});

it('should render a Typeahead element with the available reports as options', async () => {
  loadReports.mockReturnValueOnce([
    {
      id: 'a',
      name: 'Report A',
    },
    {
      id: 'b',
      name: 'Report B',
    },
  ]);
  const node = shallow(<ReportModal {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node.find('Typeahead')).toMatchSnapshot();
});

it('should call the callback when adding a report', async () => {
  loadReports.mockReturnValueOnce([
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
  const node = shallow(<ReportModal {...props} confirm={spy} />);

  runAllEffects();
  await flushPromises();

  node.find(Typeahead).prop('onChange')('a');

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    id: 'a',
    type: 'optimize_report',
  });
});

it('should show a loading message while loading available reports', () => {
  const node = shallow(<ReportModal {...props} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should contain an External Website field', () => {
  const node = shallow(<ReportModal {...props} />);

  expect(node.find(Tabs.Tab).at(1).prop('title')).toBe('External Website');
});

it('should hide the typeahead when external mode is enabled', () => {
  const node = shallow(<ReportModal {...props} />);

  node.find(Tabs).prop('onChange')('external');

  expect(node.find('Typeahead')).not.toExist();
});

it('should contain a text input field if in external source mode', () => {
  const node = shallow(<ReportModal {...props} />);

  node.find(Tabs).prop('onChange')('external');

  expect(node.find('.externalInput')).toExist();
});

it('should  disable the submit button if the url does not start with http in external mode', () => {
  const node = shallow(<ReportModal {...props} />);

  node.find(Tabs).prop('onChange')('external');
  node.find('.externalInput').prop('onChange')({
    target: {value: 'Dear computer, please show me a report. Thanks.'},
  });

  expect(node.find('[primary]')).toBeDisabled();
});

it('should contain an Text field', () => {
  const node = shallow(<ReportModal {...props} />);

  expect(node.find(Tabs.Tab).at(2).prop('title')).toBe('Text');
});

it('should contain text editor if in text report mode', () => {
  const node = shallow(<ReportModal {...props} />);

  node.find(Tabs).prop('onChange')('text');

  expect(node.find('TextEditor')).toExist();
});

it('should  disable the submit button if the text in editor is empty or too long', () => {
  const node = shallow(<ReportModal {...props} />);

  node.find(Tabs).prop('onChange')('text');

  expect(node.find('[primary]')).toBeDisabled();

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
  node.find('TextEditor').prop('onChange')(normalText);
  expect(node.find('[primary]')).not.toBeDisabled();

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
  node.find('TextEditor').prop('onChange')(tooLongText);

  expect(node.find('[primary]')).toBeDisabled();
});
