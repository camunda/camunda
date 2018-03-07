import 'raf/polyfill';
import Enzyme from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import 'jest-enzyme';
import {shim as objectValuesShim} from 'object.values';
import 'element-closest';

Enzyme.configure({adapter: new Adapter()});

const localStorageMock = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn()
};
global.localStorage = localStorageMock;

document.execCommand = jest.fn();

global.MutationObserver = class MutationObserver {
  observe() {}
};

objectValuesShim();
