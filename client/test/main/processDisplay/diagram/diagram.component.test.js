import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {Diagram, __set__, __ResetDependency__} from 'main/processDisplay/diagram/diagram.component';

const dayInMs = 24 * 60 * 60 * 1000;

describe('<Diagram>', () => {
  const diagram = 'diagram-xml';
  let filters;
  let Viewer;
  let viewer;
  let diagramNode;
  let overlays;
  let canvas;
  let elements;
  let elementRegistry;
  let node;
  let update;

  beforeEach(() => {
    overlays = {
      add: sinon.spy(),
      remove: sinon.spy()
    };

    canvas = {
      resized: sinon.spy(),
      zoom: sinon.spy()
    };

    elements = [
      {id: 'id-23'}
    ];

    elementRegistry = {
      filter: sinon.stub().returns(elements)
    };

    filters = {
      startDate: 1,
      endDate: 5 * dayInMs
    };

    Viewer = function({container}) {
      const modules = {
        overlays,
        canvas,
        elementRegistry
      };

      diagramNode = container;
      viewer = this;

      this.get = function(name) {
        return modules[name];
      };

      this.importXML = sinon.stub().callsArg(1);
    };
    __set__('Viewer', Viewer);

    ({node, update} = mountTemplate(<Diagram />));
  });

  afterEach(() => {
    __ResetDependency__('Viewer');
  });

  it('should pass diagram__holder node to Viewer constructor', () => {
    expect(diagramNode).to.have.class('diagram__holder');
  });

  it('should import xml on update', () => {
    update({diagram, filters});

    expect(viewer.importXML.calledWith(diagram)).to.eql(true);
  });

  it('should reset zoom after importing xml', () => {
    update({diagram, filters});

    expect(canvas.resized.calledOnce).to.eql(true, 'expected canvas.resized to be called');
    expect(canvas.zoom.calledWith('fit-viewport', 'auto'))
      .to.eql(true, 'expected canvas.zoom to be called with "fit-viewport", "auto"');
  });

  it('should add overlays for elements', () => {
    update({diagram, filters});

    expect(overlays.add.calledWith(elements[0].id)).to.eql(true);
  });

  describe('overlay options', () => {
    let options;

    beforeEach(() => {
      update({diagram, filters});

      ([, options] = overlays.add.firstCall.args);
    });

    it('should set position', () => {
      expect(options.position).to.eql({
        bottom: 0,
        right: 0
      });
    });

    it('should set minimal and maximal zoom for showing overlay', () => {
      expect(options.show).to.eql({
        minZoom: -Infinity,
        maxZoom: +Infinity
      });
    });

    it('should set overlay content to be between 0 and 5', () => {
      const matched = options.html.match(/[0-9]+/);

      expect(matched).to.exist;

      const number = +matched[0];

      expect(number).to.be.within(0, 5);
    });
  });

  describe('after first xml load', () => {
    beforeEach(() => {
      update({diagram, filters});
    });

    it('should not import xml when diagram did not change', () => {
      const filters = {
        startDate: 1,
        endDate: 7 * dayInMs
      };

      viewer.importXML.reset();

      update({diagram, filters});

      expect(viewer.importXML.called).to.eql(false);
    });

    it('should import xml when diagram changed', () => {
      const diagram = 'other-diagram-xml';

      viewer.importXML.reset();

      update({diagram, filters});

      expect(viewer.importXML.calledWith(diagram)).to.eql(true);
    });

    it('should remove old overlays', () => {
      const filters = {
        startDate: 1,
        endDate: 7 * dayInMs
      };

      overlays.remove.reset();

      update({diagram, filters});

      expect(overlays.remove.calledWith(elements[0].id)).to.eql(true);
    });

    it('should update overlays only when state changed', () => {
      overlays.remove.reset();
      overlays.add.reset();

      update({diagram, filters});

      expect(overlays.add.called).to.eql(false, 'expected no overlays to be added');
      expect(overlays.remove.called).to.eql(false, 'expected no overlays to be removed');
    });
  });
});
