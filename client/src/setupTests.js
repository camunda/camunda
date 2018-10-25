import 'raf/polyfill';
import Enzyme from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import 'jest-enzyme';
import {shim as objectValuesShim} from 'object.values';
import 'element-closest';

Enzyme.configure({adapter: new Adapter()});

document.execCommand = jest.fn();

global.MutationObserver = class MutationObserver {
  observe() {}
};

objectValuesShim();
