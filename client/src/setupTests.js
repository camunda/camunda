import Enzyme from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import 'jest-enzyme';

jest.mock('modules/components/Icon', () =>
  import('modules/components/__mocks__/Icon')
);

Enzyme.configure({adapter: new Adapter()});
