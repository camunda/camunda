import {expect} from 'chai';
import {mountTemplate} from 'testHelpers';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {Progress, __set__, __ResetDependency__} from 'main/footer/progress/Progress';

describe('<Progress>', () => {
  let node;
  let update;
  let loadProgress;

  beforeEach(() => {
    loadProgress = sinon.spy();
    __set__('loadProgress', loadProgress);

    ({node, update} = mountTemplate(<Progress />));
  });

  afterEach(() => {
    __ResetDependency__('loadProgress');
  });

  it('should load progress', () => {
    expect(loadProgress.calledOnce).to.eql(true);
  });

  it('should show progres when it is below 100', () => {
    update({data: {progress: 10}});

    expect(node).to.contain.text('10%');
  });

  it('should not show progres when it is 100', () => {
    update({data: {progress: 100}});

    expect(node.innerText.trim()).to.eql('');
  });

  it('should not show import by default', () => {
    expect(node.innerText.trim()).to.eql('');
  });
});
