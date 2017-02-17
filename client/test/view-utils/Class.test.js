import {expect} from 'chai';
import {jsx, Class, isTruthy} from 'view-utils';
import {mountTemplate, observeFunction} from 'testHelpers';

describe('<Class>', () => {
  const className = 'classical-class';
  let predicate;
  let node;
  let update;
  let target;

  beforeEach(() => {
    predicate = observeFunction(isTruthy);

    ({node, update} = mountTemplate(<div id="target">
      <Class className={className} selector="error" predicate={predicate} />
    </div>));

    target = node.querySelector('#target');
  });

  it('should not add the class before update', () => {
    expect(target).not.to.have.class(className);
  });

  it('should add class when predicate returns true for selected state', () => {
    update({
      error: true
    });

    expect(target).to.have.class(className);
  });

  it('should remove class when predicate return false for selected state', () => {
    update({
      error: true
    });
    update({
      error: false
    });

    expect(target).not.to.have.class(className);
  });

  it('should class predicate with selected state on update', () => {
    const error = 'error';

    update({error});

    expect(predicate.calledWith(error)).to.eql(true);
  });
});
