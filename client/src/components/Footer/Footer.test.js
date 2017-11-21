import React from 'react';
import { shallow } from 'enzyme';

import Footer from './Footer';
import {getConnectionStatus, getImportProgress} from './service';


jest.mock('./service', () => {return {
  getConnectionStatus: jest.fn(() => {
    return ({
    "engineConnections" : {
      "property1" : true,
      "property2" : false
    },
    "connectedToElasticsearch" : true
    })
  }),
  getImportProgress: jest.fn()
}});

it('renders without crashing', () => {
  shallow(<Footer />);
});

it('includes the version number provided as property', () => {
  const version = 'alpha';

  const node = shallow(<Footer version={version} />);
  expect(node).toIncludeText(version);
});

it('displays the import progress', () => {
  const node = shallow(<Footer version='2.0.0'/>);
  expect(node.find('.import-progress-footer')).toBePresent();
});

it('displays the connection status', () => {
  const node = shallow(<Footer version='2.0.0'/>);
  expect(node.find('.connection-status-footer')).toBePresent();
});

it('should load import progress', () => {
  shallow(<Footer version='2.0.0'/>);
  expect(getImportProgress).toBeCalled();
});

it('should load connection status', () => {
  shallow(<Footer version='2.0.0'/>);
  expect(getConnectionStatus).toBeCalled();
});
