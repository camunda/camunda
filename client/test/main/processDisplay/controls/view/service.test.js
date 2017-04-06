import {expect} from 'chai';
import sinon from 'sinon';
import {getView, __set__, __ResetDependency__} from 'main/processDisplay/controls/view/service';

describe('View service', () => {
  const view = 'dd';
  let getLastRoute;

  beforeEach(() => {
    getLastRoute = sinon.stub().returns({
      params: {view}
    });
    __set__('getLastRoute', getLastRoute);
  });

  afterEach(() => {
    __ResetDependency__('getLastRoute');
  });

  describe('getView', () => {
    it('should return view', () => {
      expect(getView()).to.eql(view);
    });
  });
});
