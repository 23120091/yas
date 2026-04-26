import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import LatestRatings from '../modules/home/components/LatestRatings';

// mock router
const pushMock = jest.fn();

jest.mock('next/router', () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

// mock API
jest.mock('modules/rating/services/RatingService', () => ({
  getLatestRatings: jest.fn(),
}));

jest.mock('@catalogServices/ProductService', () => ({
  getProduct: jest.fn(),
}));

const { getLatestRatings } = require('modules/rating/services/RatingService');
const { getProduct } = require('@catalogServices/ProductService');

describe('LatestRatings', () => {
  test('show loading', () => {
    getLatestRatings.mockReturnValue(new Promise(() => {}));

    render(<LatestRatings />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  test('show empty', async () => {
    getLatestRatings.mockResolvedValue([]);

    render(<LatestRatings />);

    await waitFor(() => {
      expect(screen.getByText(/no ratings available/i)).toBeInTheDocument();
    });
  });

  test('click details triggers router', async () => {
    getLatestRatings.mockResolvedValue([
      {
        id: 1,
        productId: 10,
        productName: 'Product A',
        content: 'Good',
        createdOn: new Date().toISOString(),
      },
    ]);

    getProduct.mockResolvedValue({ id: 10 });

    render(<LatestRatings />);

    await waitFor(() => {
      expect(screen.getByText('Product A')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText(/details/i));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalled();
    });
  });
});