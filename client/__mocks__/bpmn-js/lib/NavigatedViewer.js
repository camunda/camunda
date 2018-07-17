class Viewer {
  constructor({container, bpmnRenderer} = {}) {
    this.canvas = {zoom: jest.fn()};
    this.zoomScroll = {stepZoom: jest.fn()};
    this.container = container;
    this.bpmnRenderer = bpmnRenderer;
    this.elementRegistry = 'foo';
  }

  importXML = jest.fn((_, callback) => {
    callback();
  });

  detach = jest.fn();

  get = key => this[key];
}

export default Viewer;
