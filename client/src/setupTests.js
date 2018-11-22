import Enzyme from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import 'jest-enzyme';

// mock localStorage
const localStorageMock = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn()
};
global.localStorage = localStorageMock;

// configure enzyme
Enzyme.configure({adapter: new Adapter()});

// mock date util
jest.mock('modules/utils/date');
