import {expect} from 'chai';
import {mountTemplate} from 'testHelpers';
import {jsx, Attribute, isTruthy} from 'view-utils';

describe('<Attribute>', () => {
  const prop = 'prop';
  const attribute = 'attr';
  let node;
  let update;
  let button;

  describe('without predicate', () => {
    beforeEach(() => {
      ({node, update} = mountTemplate(<button>
        <Attribute selector={prop} attribute={attribute} />
      </button>));

      button = node.querySelector('button');
    });

    it('should not set attribute before update', () => {
      expect(button.getAttribute(attribute)).to.eql(null);
    });

    it('should set attribute from state', () => {
      const value = 'val-1';

      update({
        [prop]: value
      });

      expect(button.getAttribute(attribute)).to.eql(value);
    });
  });

  describe('with predicate', () => {
    beforeEach(() => {
      ({node, update} = mountTemplate(<button>
        <Attribute selector={prop} attribute={attribute} predicate={isTruthy} />
      </button>));

      button = node.querySelector('button');
    });

    it('should not set attribute before update', () => {
      expect(button.getAttribute(attribute)).to.eql(null);
    });

    it('should set attribute from state', () => {
      const value = 'val-1';

      update({
        [prop]: value
      });

      expect(button.getAttribute(attribute)).to.eql(value);
    });

    it('should remove attribute when predicate returns false', () => {
      button.setAttribute(attribute, 'something');

      update({});

      expect(button.getAttribute(attribute)).to.eql(null);
    });
  });
});
