import {expect} from 'chai';
import sinon from 'sinon';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {jsx} from 'view-utils';
import {Header, __set__, __ResetDependency__} from 'main/header/Header';

describe('<Header>', () => {
  let node;
  let router;

  beforeEach(() => {
    router = {
      goTo: sinon.spy()
    };

    __set__('router', router);

    ({node} = mountTemplate(<Header/>));
  });

  afterEach(() => {
    __ResetDependency__('router');
  });

  it('should contain header text', () => {
    expect(node).to.contain.text('Camunda Optimize');
  });

  it('should redirect to default route on click', () => {
    triggerEvent({
      node,
      selector: '.navbar-brand',
      eventName: 'click'
    });

    expect(router.goTo.calledWith('default', {})).to.eql(true);
  });
});
