import {expect} from 'chai';
import {hasClass, addClass, removeClass} from 'view-utils/classFunctions';
import sinon from 'sinon';

describe('class functions', () => {
  const className = 'classical-class';

  describe('on element with classList', () => {
    let classList;
    let element;

    beforeEach(() => {
      classList = {
        contains: sinon.stub().returnsArg(0),
        add: sinon.spy(),
        remove: sinon.spy()
      };

      element = {
        classList
      };
    });

    describe('hasClass', () => {
      it('should call classList.contains to check if element has a given class', () => {
        expect(hasClass(element, className)).to.eql(className);
        expect(classList.contains.calledWith(className)).to.eql(true);
      });
    });

    describe('addClass', () => {
      it('should call classList.add to add class', () => {
        addClass(element, className);

        expect(classList.add.calledWith(className)).to.eql(true);
      });
    });

    describe('removeClass', () => {
      it('should call classList.remove to remove class', () => {
        removeClass(element, className);

        expect(classList.remove.calledWith(className)).to.eql(true);
      });
    });
  });

  describe('on element without classList', () => {
    let element;

    beforeEach(() => {
      element = {
        className
      };
    });

    describe('hasClass', () => {
      it('should return true when className has given class', () => {
        expect(hasClass(element, className)).to.eql(true);
      });

      it('should return false when className does not have given class', () => {
        expect(hasClass(element, 'other-class-2233')).to.eql(false);
      });
    });

    describe('addClass', () => {
      it('should add new class', () => {
        const otherClass = 'some-other-class';

        addClass(element, otherClass);

        expect(element.className).to.contain(otherClass);
      });

      it('should not duplicate class', () => {
        addClass(element, className);

        expect(element.className).to.eql(className);
      });
    });

    describe('removeClass', () => {
      it('should remove class', () => {
        removeClass(element, className);

        expect(element.className).to.not.contain(className);
      });
    });
  });
});
