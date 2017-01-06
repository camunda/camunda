import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {RouteView, __set__, __ResetDependency__} from 'router/routeView.component';

describe('<RouteView>', () => {
  let template;
  let node;

  beforeEach(() => {
    template = <RouteView>blah</RouteView>;
    ({node} = mountTemplate(template));
  });

  it('should add render children', () => {
    expect(node).to.contain.text('blah');
  });

  describe('predicate with string name', () => {
    const name = 'd';
    let getLastRoute;
    let predicate;

    beforeEach(() => {
      getLastRoute = sinon
        .stub()
        .returns({
          name: 'd'
        });

      __set__('getLastRoute', getLastRoute);

      template = <RouteView name={name}>blah</RouteView>;
      ({node} = mountTemplate(template));
      ({predicate} = template);
    });

    afterEach(() => {
      __ResetDependency__('getLastRoute');
    });

    it('should return true when route matches', () => {
      expect(predicate()).to.eql(true);
    });

    it('should return false when route does not match', () => {
      getLastRoute.returns({
        name: 'other'
      });

      expect(predicate()).to.eql(false);
    });

    it('should return false when there is no last route', () => {
      getLastRoute.returns(null);

      expect(predicate()).to.eql(false);
    });
  });

  describe('predicate with regular expression name', () => {
    const name = /^d/;
    let getLastRoute;
    let predicate;

    beforeEach(() => {
      getLastRoute = sinon
        .stub()
        .returns({
          name: 'd'
        });

      __set__('getLastRoute', getLastRoute);

      template = <RouteView name={name}>blah</RouteView>;
      ({node} = mountTemplate(template));
      ({predicate} = template);
    });

    afterEach(() => {
      __ResetDependency__('getLastRoute');
    });

    it('should return true when route matches', () => {
      expect(predicate()).to.eql(true);
    });

    it('should return false when route does not match', () => {
      getLastRoute.returns({
        name: 'other'
      });

      expect(predicate()).to.eql(false);
    });

    it('should return false when there is no last route', () => {
      getLastRoute.returns(null);

      expect(predicate()).to.eql(false);
    });
  });
});
