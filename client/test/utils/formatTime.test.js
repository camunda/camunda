import {expect} from 'chai';
import sinon from 'sinon';
import {formatTime, ms, d, m, s, h, createDelayedTimePrecisionElement,
        __set__, __ResetDependency__} from 'utils/formatTime';

describe('formatTime', () => {
  it('should format ms input into human readable string', () => {
    const time = 3 * d + 20 * m + 37 * s;

    expect(formatTime(time)).to.eql('3d&nbsp;20m&nbsp;37s');
  });

  it('should return string with only the specified precision of non-empty units of time', () => {
    const time = 3 * h + 20 * m + 10 * s + 11 * ms;

    expect(formatTime(time, {precision: 2})).to.eql('3h&nbsp;20m');
  });

  it('should handle zero well', () => {
    expect(formatTime(0)).to.eql('0ms');
  });

  it('should single unit well', () => {
    expect(formatTime(5 * h)).to.eql('5h');
  });

  it('should return raw values if requested', () => {
    const result = formatTime(400, {returnRaw: true});

    expect(result).to.be.an('array');
    expect(result[0].howMuch).to.eql(400);
    expect(result[0].name).to.eql('ms');
  });

  it('should return raw value even for 0 value', () => {
    const result = formatTime(0, {returnRaw: true});

    expect(result).to.be.an('array');
    expect(result).to.be.empty;
  });
});

describe('createDelayedTimePrecisionElement', () => {
  let $window;

  beforeEach(() => {
    $window = {
      setTimeout: sinon.spy()
    };

    __set__('$window', $window);
  });

  afterEach(() => {
    __ResetDependency__('$window');
  });

  it('should return a dom element', () => {
    const returnValue = createDelayedTimePrecisionElement(123, {initialPrecision: 2, delay: 500});

    expect(typeof returnValue).to.eql('object');
    expect(returnValue.tagName).to.exist;
  });

  it('should contain the formatted time with the provided initial precision', () => {
    const returnValue = createDelayedTimePrecisionElement(3*d + 5*h, {initialPrecision: 2, delay: 500});

    expect(returnValue.textContent).to.eql('3d\xa05h');
  });

  it('should contain an ellipsis when value has more precision than initially shown', () => {
    const returnValue = createDelayedTimePrecisionElement(3*d + 5*h, {initialPrecision: 1, delay: 500});

    expect(returnValue.textContent).to.eql('3d\u2026');
  });

  it('should replace its contents after the provided delay', () => {
    const returnValue = createDelayedTimePrecisionElement(3*d + 5*h, {initialPrecision: 1, delay: 500});

    $window.setTimeout.getCall(0).args[0]();

    expect($window.setTimeout.getCall(0).args[1]).to.eql(500);
    expect(returnValue.textContent).to.eql('3d\xa05h');
  });

  it('should not replace the contents if the initial precision was sufficient', () => {
    createDelayedTimePrecisionElement(3*d + 5*h, {initialPrecision: 2, delay: 500});

    expect($window.setTimeout.called).to.eql(false);
  });
});
