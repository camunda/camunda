import React from 'react';
import {shallow, mount} from 'enzyme';

import Footer from './Footer';
import {getConnectionStatus, getImportProgress} from './service';

jest.mock('./service', () => {
  return {
    getConnectionStatus: jest.fn(() => {
      return {
        engineConnections: {
          property1: true,
          property2: false
        },
        connectedToElasticsearch: true
      };
    }),
    getImportProgress: jest.fn(() => {
      return {
        progress: 97
      };
    })
  };
});

it('renders without crashing', () => {
  shallow(<Footer />);
});

it('includes the version number provided as property', () => {
  const version = 'alpha';

  const node = shallow(<Footer version={version} />);
  expect(node).toIncludeText(version);
});

it('displays the loading indicator if import progress is less than 100', () => {
  const node = mount(<Footer />);

  node.setState({
    engineConnections: {
      property1: true,
      property2: false
    },
    connectedToElasticsearch: true
  });

  expect(node.find('.is-in-progress')).toBePresent();
});

it('does not display the loading indicator if import progress is 100', () => {
  const node = mount(<Footer />);

  node.setState({
    importProgress: 100,
    engineConnections: {
      property1: true,
      property2: false
    },
    connectedToElasticsearch: true
  });

  expect(node.find('.is-in-progress')).not.toBePresent();
});

it('should load import progress', () => {
  shallow(<Footer version="2.0.0" />);
  expect(getImportProgress).toBeCalled();
});

it('displays the connection status', () => {
  const node = mount(<Footer version="2.0.0" />);

  node.setState({
    engineConnections: {
      engine1: true
    }
  });

  expect(node.find('.Footer__connect-status')).toBePresent();
});

it('should load connection status', () => {
  shallow(<Footer version="2.0.0" />);

  expect(getConnectionStatus).toBeCalled();
});
