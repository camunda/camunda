import Enzyme from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import 'jest-enzyme';

jest.mock('modules/components/Icon', () =>
  require('modules/components/__mocks__/Icon')
);

const localStorageMock = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn()
};
global.localStorage = localStorageMock;

Enzyme.configure({adapter: new Adapter()});
