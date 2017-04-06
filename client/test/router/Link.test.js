import {expect} from 'chai';
import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {Link, __set__, __ResetDependency__} from 'router/Link';
import sinon from 'sinon';

describe('<Link>', () => {
  let node;
  let update;
  let state;
  let selector;
  let router;

  beforeEach(() => {
    router = {
      goTo: sinon.spy(),
      getUrl: sinon.stub().returns('url')
    };
    __set__('router', router);

    state =  {
      name: 'route-1',
      params: 'params'
    };

    selector = sinon.stub().returns(state);

    ({node, update} = mountTemplate(
      <a>
        <Link selector={selector} />
      </a>
    ));

    update();
  });

  afterEach(() => {
    __ResetDependency__('router');
  });

  it('should go to route provided by state', () => {
    triggerEvent({
      node,
      selector: 'a',
      eventName: 'click'
    });

    expect(router.goTo.calledWith(state.name, state.params)).to.eql(true);
  });

  it('should set href attribute on "A" element', () => {
    expect(node.querySelector('a')).to.have.attr('href', 'url');
  });
});
