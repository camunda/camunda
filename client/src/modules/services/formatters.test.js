import {frequency as frequencyFormatter, duration as durationFormatter} from './formatters';

const separator = '\u202F';
const nbsp = '\u00A0';

describe('frequencyFormatter', () => {
  it('should do nothing for numbers < 1000', () => {
    expect(frequencyFormatter(4)).toBe('4');
    expect(frequencyFormatter(194)).toBe('194');
  });

  it('should handle zero well', () => {
    expect(frequencyFormatter(0)).toBe('0');
  });

  it('should add thousand separator at correct position', () => {
    expect(frequencyFormatter(6934)).toBe(`6${separator}934`);
    expect(frequencyFormatter(61934)).toBe(`61${separator}934`);
    expect(frequencyFormatter(761934)).toBe(`761${separator}934`);
  });

  it('should add multiple thousand separators', () => {
    expect(frequencyFormatter(2349875982)).toBe(`2${separator}349${separator}875${separator}982`);
  });
});

describe('durationFormatter', () => {
  it('should format ms input into human readable string', () => {
    const time = 27128;

    expect(durationFormatter(time)).toBe(`27s${nbsp}128ms`);
  });

  it('should handle zero well', () => {
    expect(durationFormatter(0)).toBe('0ms');
  });

  it('should single unit well', () => {
    expect(durationFormatter(5 * 60 * 60 * 1000)).toBe('5h');
  });

  it('should handle single millisecond durations', () => {
    expect(durationFormatter(1)).toBe('1ms');
  });
});
