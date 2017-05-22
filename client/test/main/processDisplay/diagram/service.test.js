import {expect} from 'chai';
import sinon from 'sinon';
import {addDiagramTooltip} from 'main/processDisplay/diagram/service';

describe('Diagram service', () => {
  const diagramElement = 'act1';

  let viewer;
  let addFunction;
  let diagramGraphics;

  beforeEach(() => {
    addFunction = sinon.spy();

    diagramGraphics = document.createElement('div');
    diagramGraphics.innerHTML = '<div class="djs-hit" width="20"></div>';

    viewer = {
      get: sinon.stub().returns({
        getGraphics: sinon.stub().returns(diagramGraphics),
        add: addFunction,
        get: sinon.stub().returns({
          x: 0,
          y: 0
        }),
        viewbox: sinon.stub().returns({
          x: 0,
          y: 0,
          width: 40,
          height: 30
        })
      })
    };
  });

  it('should add overlays on an element', () => {
    const tooltipNode = document.createElement('span');

    addDiagramTooltip(viewer, diagramElement, tooltipNode);

    expect(addFunction.calledWith(diagramElement)).to.eql(true);
  });

  it('should work with non-node formatter return values', () => {
    addDiagramTooltip(viewer, diagramElement, 'asdf');

    const node = addFunction.getCall(0).args[1].html;

    expect(node.textContent).to.contain('asdf');
  });
});
