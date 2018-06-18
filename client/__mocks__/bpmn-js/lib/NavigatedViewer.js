class Viewer {
  constructor() {
    this.canvas = {zoom: jest.fn()};
    this.zoomScroll = {stepZoom: jest.fn()};
  }

  importError = null;

  mockSuccessfulImportXML = () => {
    this.importError = null;
  };

  mockUnsuccessfulImportXML = () => {
    this.importError = 'Error importing diagram';
  };

  importXML = (_, callback) => {
    callback(this.importError);
  };

  get = key => this[key];
}

export default Viewer;
