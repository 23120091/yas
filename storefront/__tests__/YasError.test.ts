import { YasError } from '../common/services/errors/YasError';

describe('YasError', () => {
  test('default constructor', () => {
    const err = new YasError();

    expect(err.message).toBe('unknown');
    expect(err.status).toBe(500);
    expect(err.title).toBe('Unknown error');
    expect(err.details).toBe('unknown');
    expect(err.fieldErrors).toEqual([]);
  });

  test('use fieldErrors as message', () => {
    const err = new YasError({
      fieldErrors: ['Invalid input'],
    });

    expect(err.message).toBe('Invalid input');
    expect(err.fieldErrors).toEqual(['Invalid input']);
  });

  test('use statusCode if status not provided', () => {
    const err = new YasError({
      statusCode: '404',
    });

    expect(err.status).toBe(404);
  });

  test('use explicit status over statusCode', () => {
    const err = new YasError({
      status: 400,
      statusCode: '500',
    });

    expect(err.status).toBe(400);
  });

  test('custom title and detail', () => {
    const err = new YasError({
      title: 'Error title',
      detail: 'Error detail',
    });

    expect(err.title).toBe('Error title');
    expect(err.details).toBe('Error detail');
    expect(err.message).toBe('Error detail');
  });
});