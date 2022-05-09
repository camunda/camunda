/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ShareEntity from './ShareEntity';

const props = {
  type: 'report',
  shareEntity: jest.fn(),
  revokeEntitySharing: jest.fn(),
  getSharedEntity: jest.fn(),
};

beforeAll(() => {
  const windowProps = JSON.stringify(window.location);
  delete window.location;

  Object.defineProperty(window, 'location', {
    value: JSON.parse(windowProps),
  });
  window.location.href = 'http://example.com/#/dashboard/1';
});

it('should render without crashing', () => {
  shallow(<ShareEntity {...props} />);
});

it('should initially get already shared entities', () => {
  shallow(<ShareEntity {...props} />);

  expect(props.getSharedEntity).toHaveBeenCalled();
});

it('should share entity if is checked', () => {
  props.getSharedEntity.mockReturnValue(10);

  const node = shallow(<ShareEntity {...props} />);

  node.instance().toggleValue({target: {checked: true}});

  expect(props.shareEntity).toHaveBeenCalled();
});

it('should delete entity if sharing is revoked', () => {
  props.getSharedEntity.mockReturnValue(10);

  const node = shallow(<ShareEntity {...props} />);

  node.instance().toggleValue({target: {checked: false}});

  expect(props.revokeEntitySharing).toHaveBeenCalled();
});

it('should construct special link', () => {
  const node = shallow(<ShareEntity type="report" {...props} />);

  node.setState({loaded: true, id: 10});

  expect(node.find('CopyToClipboard').at(0)).toHaveProp(
    'value',
    'http://example.com/external/#/share/report/10'
  );
});

it('should construct special link for embedding', () => {
  const node = shallow(<ShareEntity type="report" {...props} />);
  Object.defineProperty(window.location, 'origin', {
    value: 'http://example.com',
  });

  node.setState({loaded: true, id: 10});

  const clipboardValue = node.find('CopyToClipboard').at(1).prop('value');

  expect(clipboardValue).toContain('<iframe src="http://example.com/external/#/share/report/10');
  expect(clipboardValue).toContain('mode=embed');
});

it('should include filters', async () => {
  const node = shallow(
    <ShareEntity
      {...props}
      type="dashboard"
      filter={[{data: null, type: 'runningInstancesOnly'}]}
    />
  );

  await flushPromises();

  node.find('.includeFilters [type="checkbox"]').simulate('change', {target: {checked: true}});

  const link = node.find('CopyToClipboard').at(0).prop('value');
  expect(link).toContain('?filter=');
  expect(link).toContain('runningInstancesOnly');
});

it('should display a loading indicator', () => {
  const node = shallow(<ShareEntity {...props} />);

  expect(node.find('LoadingIndicator')).toExist();
});
