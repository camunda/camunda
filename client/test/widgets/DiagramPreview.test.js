import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {DiagramPreview, __set__, __ResetDependency__} from 'widgets/DiagramPreview';

describe('<DiagramPreview>', () => {
  const diagramXml = 'diagram-xml';

  let Viewer;
  let viewer;
  let canvas;
  let update;
  let md5;
  let queue;
  let done;

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

    md5 = sinon.stub().returns(Math.random());
    __set__('md5', md5);

    done = sinon.spy();

    queue = {
      addTask: sinon.stub()
        .callsArgWith(0, done)
        .returns(() => true)
    };
    __set__('queue', queue);

    ({update} = mountTemplate(
      <DiagramPreview />
    ));

    update(diagramXml);
  });

  afterEach(() => {
    __ResetDependency__('Viewer');
    __ResetDependency__('md5');
    __ResetDependency__('queue');
  });

  it('should import xml on update', () => {
    expect(viewer.importXML.calledWith(diagramXml)).to.eql(true);
    expect(done.called).to.eql(true);
  });

  it('should reset zoom after importing xml', () => {
    expect(canvas.resized.calledOnce).to.eql(true, 'expected canvas.resized to be called');
    expect(canvas.zoom.calledWith('fit-viewport', 'auto'))
      .to.eql(true, 'expected canvas.zoom to be called with "fit-viewport", "auto"');
  });

  it('should not import xml on second update', () => {
    update(diagramXml);

    expect(viewer.importXML.calledOnce).to.eql(true);
  });
});
