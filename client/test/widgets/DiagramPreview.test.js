import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {createDiagramPreview, __set__, __ResetDependency__} from 'widgets/DiagramPreview';

describe('<DiagramPreview>', () => {
  const diagramXml = 'diagram-xml';

  let Viewer;
  let viewer;
  let canvas;
  let update;
  let queue;
  let done;
  let node;
  let DiagramPreview;

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
      addTask: sinon.stub()
        .callsArgWith(0, done)
        .returns(() => true)
    };
    __set__('queue', queue);

    DiagramPreview = createDiagramPreview();

    ({node, update} = mountTemplate(
      <DiagramPreview />
    ));

    update(diagramXml);
  });

  afterEach(() => {
    __ResetDependency__('Viewer');
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

  it('should display error when diagram is not given', () => {
    update(null);

    expect(node).to.contain.text('No diagram');
    expect(node.querySelector('.diagram-error').style.display).to.eql('block');
  });

  describe('setLoading', () => {
    it('should hide loader when used with false', () => {
      //given
      DiagramPreview.setLoading(true);
      expect(node.querySelector('.diagram-loading').style.display).to.eql('block');

      //when
      DiagramPreview.setLoading(false);

      //expected
      expect(node.querySelector('.diagram-loading').style.display).to.eql('none');
    });

    it('should show loader when used with true', () => {
      //given
      DiagramPreview.setLoading(false);
      expect(node.querySelector('.diagram-loading').style.display).to.eql('none');

      //when
      DiagramPreview.setLoading(true);

      //expected
      expect(node.querySelector('.diagram-loading').style.display).to.eql('block');
    });

    it('should import diagram when loading is in progress', () => {
      DiagramPreview.setLoading(true);

      update(diagramXml);

      expect(viewer.importXML.calledTwice).to.eql(true);
    });
  });
});
