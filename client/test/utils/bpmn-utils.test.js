import {expect} from 'chai';
import sinon from 'sinon';
import {updateOverlayVisibility, isBpmnType} from 'utils/bpmn-utils';

describe('Bpmn Utilities', () => {
  describe('isBpmnType', () => {
    let element;
    const TYPE = 'TYPE';

    beforeEach(() => {
      const $instanceOf = sinon.stub();

      $instanceOf.withArgs('bpmn:' + TYPE).returns(true);
      $instanceOf.returns(false);

      element = {
        type: TYPE,
        businessObject: {
          $instanceOf
        }
      };
    });

    it('should return true if the element is of a specific type', () => {
      expect(isBpmnType(element, TYPE)).to.eql(true);
    });

    it('should return false if the element is not of a specific type', () => {
      expect(isBpmnType(element, 'anotherType')).to.eql(false);
    });

    it('should return true if element is of the type of one array entry', () => {
      expect(isBpmnType(element, ['a', 'b', TYPE, 'c'])).to.eql(true);
    });

    it('should return false if element type is not in array', () => {
      expect(isBpmnType(element, ['a', 'b', 'c'])).to.eql(false);
    });
  });

  describe('update overlay visibility', () => {
    let viewer;
    let elementList;

    beforeEach(() => {
      elementList = [
        {html: document.createElement('div')},
        {html: document.createElement('div')}
      ];

      viewer = {
        get: sinon.stub().returns({
          get: sinon.stub().returns(elementList)
        })
      };
    });
    it('should hide all elements except the selected one', () => {
      updateOverlayVisibility(viewer, elementList[0], 'SOME_TYPE');

      expect(elementList[0].html.style.display).to.eql('block');
      expect(elementList[1].html.style.display).to.eql('none');
    });

    it('should not hide elements that should be kept open', () => {
      const alwaysOpen = {
        html: document.createElement('div'),
        keepOpen: true
      };

      elementList.push(alwaysOpen);
      updateOverlayVisibility(viewer, elementList[0], 'SOME_TYPE');

      expect(alwaysOpen.html.style.display).to.not.eql('none');
    });
  });
});
