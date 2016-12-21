import PromiseMock from 'promise-mock';

export function setupPromiseMocking() {
  beforeEach(PromiseMock.install);
  afterEach(PromiseMock.uninstall);
}
