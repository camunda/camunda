import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessInstanceCount, __set__, __ResetDependency__} from 'main/processDisplay/views/ProcessInstanceCount';

describe('<ProcessInstanceCount>', () => {
  let getInstanceCount;
  let node;
  let update;

  beforeEach(() => {
    getInstanceCount = sinon.stub().returnsArg(0);
    __set__('getInstanceCount', getInstanceCount);

    ({node, update} = mountTemplate(<ProcessInstanceCount />));
  });

  afterEach(() => {
    __ResetDependency__('getInstanceCount');
  });

  it('should display the instance count', () => {
    update(123);

    expect(node.querySelector('.count')).to.exist;
    expect(node.querySelector('.count').textContent).to.eql('123');
  });

  it('should have a thousands separator', () => {
    update(12345);

    expect(node.querySelector('.count').textContent).to.eql('12\u202F345');
  });
});
