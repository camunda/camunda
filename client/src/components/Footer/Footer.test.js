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

jest.mock('components', () => {
  return {
    ProgressBar: props => <div className="ProgressBar" {...props} />
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

it('displays the import status if it is less than 100', () => {
  const node = mount(<Footer />);

  node.setState({
    importProgress: 50
  });

  expect(node.find('.Footer__import-status')).toBePresent();
});

it('does not display the import status if it is 100', () => {
  const node = mount(<Footer />);

  node.setState({
    importProgress: 100
  });

  expect(node.find('.Footer__import-status')).not.toBePresent();
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
