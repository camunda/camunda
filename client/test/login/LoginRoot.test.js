import {expect} from 'chai';
import {createMockComponent, mountTemplate} from 'testHelpers';
import {jsx} from 'view-utils';
import {LoginRoot, getLogin} from 'login/LoginRoot';

describe('<LoginRoot>', () => {
  let Child;
  let node;
  let update;

  beforeEach(() => {
    Child = createMockComponent('child');

    ({node, update} = mountTemplate(<LoginRoot>
      <Child />
    </LoginRoot>));
  });

  it('should add child as child', () => {
    expect(node).to.contain.text('child');
  });

  describe('getLogin', () => {
    it('should return null as login before update', () => {
      expect(getLogin()).to.eql(null);
    });

    it('should return last login passed with state', () => {
      const login = 'log-23-amanda';

      update({login});

      expect(getLogin()).to.eql(login);
    });
  });
});
