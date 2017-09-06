import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {DiagramPreview, __set__, __ResetDependency__} from 'widgets/DiagramPreview';
import React from 'react';
import {mount} from 'enzyme';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<DiagramPreview>', () => {
  const diagramXml = 'diagram-xml';

  let Viewer;
  let viewer;
  let onLoaded;
  let canvas;
  let queue;
  let done;
  let node;

  beforeEach(() => {
    canvas = {
      resized: sinon.spy(),
      zoom: sinon.spy()
    };

    Viewer = function({container}) {
      const modules = {
        canvas
      };

      viewer = this;

      this.get = function(name) {
        return modules[name];
      };

      this.importXML = sinon.stub().callsArg(1);
    };
    __set__('Viewer', Viewer);

    done = sinon.spy();

    queue = {
      addTask: sinon.spy()
    };
    __set__('queue', queue);

    onLoaded = sinon.spy();

    node = mount(
      <DiagramPreview diagram={diagramXml} loading={true} onLoaded={onLoaded} />
    );
  });

  afterEach(() => {
    __ResetDependency__('Viewer');
    __ResetDependency__('queue');
  });

  it('should import xml on update', () => {
    queue.addTask.firstCall.args[0](done);

    expect(viewer.importXML.calledWith(diagramXml)).to.eql(true);
    expect(done.called).to.eql(true);
  });

  it('should reset zoom after importing xml', () => {
    queue.addTask.firstCall.args[0](done);

    expect(canvas.resized.calledOnce).to.eql(true, 'expected canvas.resized to be called');
    expect(canvas.zoom.calledWith('fit-viewport', 'auto'))
      .to.eql(true, 'expected canvas.zoom to be called with "fit-viewport", "auto"');
  });

  it('should display error when diagram is not given', () => {
    node = mount(
      <DiagramPreview loading={true} onLoaded={onLoaded} />
    );

    expect(node).to.contain.text('No diagram');
    expect(node).not.to.contain('.diagram__holder');
  });
});
