import {expect} from 'chai';
import sinon from 'sinon';
import {updateOverlayVisibility} from 'utils/bpmn-utils';

describe('Bpmn Utilities', () => {
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
    it('should set the opacity for all elements except the selected one to 0', () => {
      updateOverlayVisibility(viewer, elementList[0], 'SOME_TYPE');

      expect(elementList[0].html.style.opacity).to.eql('1');
      expect(elementList[1].html.style.opacity).to.eql('0');
    });
  });
});
