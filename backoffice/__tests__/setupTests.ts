import '@testing-library/jest-dom';

// mock fetch (fix lỗi fetch is not defined)
global.fetch = jest.fn(() =>
  Promise.resolve({
    json: () => Promise.resolve([]),
  })
) as jest.Mock;