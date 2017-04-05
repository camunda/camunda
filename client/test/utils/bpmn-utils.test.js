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
