import {expect} from 'chai';
import {reducer, createHoverElementAction} from 'main/processDisplay/diagram/reducer';

describe('diagram reducer', () => {
  const diagramElement = {id: 'elementId'};

  describe('actions', () => {
    it('should set the hovered diagram element', () => {
      const {hovered} = reducer(undefined, createHoverElementAction(diagramElement));

      expect(hovered).to.exist;
      expect(hovered).to.eql(diagramElement.id);
    });
  });
});
