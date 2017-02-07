import {expect} from 'chai';
import {mountTemplate} from 'testHelpers';
import {createReferenceComponent, jsx, DESTROY_EVENT} from 'view-utils';

describe('createReferenceComponent', () => {
  let target;
  let Reference;

  beforeEach(() => {
    target = {};
    Reference = createReferenceComponent(target);
  });

  it('should return function', () => {
    expect(typeof Reference).to.eql('function');
  });

  describe('<Reference>', () => {
    let node;
    let eventsBus;

    beforeEach(() => {
      ({node, eventsBus} = mountTemplate(
        <div className="some-class">
          <Reference name="some" />
          <button>
            <Reference name="button" />
          </button>
        </div>
      ));
    });

    it('should create all dom nodes', () => {
      expect(node.querySelector('.some-class')).to.exist;
      expect(node.querySelector('button')).to.exist;
    });

    it('should set some property on target object to div node', () => {
      expect(target.some).to.have.class('some-class');
      expect(target.some.tagName).to.eql('DIV');
    });

    it('should set button propert on target to button node', () => {
      expect(target.button).to.eql(node.querySelector('button'));
      expect(target.button.tagName).to.eql('BUTTON');
    });

    it('should remove property from target on destroy event', () => {
      eventsBus.fireEvent(DESTROY_EVENT);

      expect(target.button).to.eql(undefined);
      expect(target.some).to.eql(undefined);
    });
  });
});
