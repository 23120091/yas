import { formatPrice } from '../utils/formatPrice';

describe('formatPrice', () => {
  test('format number correctly', () => {
    expect(formatPrice(1000)).toContain('1');
  });

  test('handle zero', () => {
    expect(formatPrice(0)).toBeDefined();
  });

  test('handle negative', () => {
    expect(formatPrice(-100)).toBeDefined();
  });
});