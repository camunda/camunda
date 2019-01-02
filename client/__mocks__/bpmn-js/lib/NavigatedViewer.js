class Viewer {
  constructor({container, bpmnRenderer} = {}) {
    this.canvas = {
      zoom: jest.fn(),
      addMarker: jest.fn(),
      removeMarker: jest.fn(),
      resized: jest.fn()
    };
    this.zoomScroll = {stepZoom: jest.fn()};
    this.container = container;
    this.bpmnRenderer = bpmnRenderer;
    this.elementRegistry = {
      getGraphics: jest.fn(() => ({
        querySelector: jest.fn(() => ({setAttribute: jest.fn()}))
      }))
    };
    this.eventBus = {on: jest.fn()};
    this.overlays = {add: jest.fn(), remove: jest.fn()};
  }

  importDefinitions = jest.fn((_, callback) => {
    callback();
  });

  detach = jest.fn();

  get = key => this[key];
}

export default Viewer;
