import { render, screen, waitFor } from '@testing-library/react';
import LatestProducts from '../modules/home/components/LatestProducts';

// mock API
jest.mock('@catalogServices/ProductService', () => ({
  getLatestProducts: jest.fn(),
}));

const { getLatestProducts } = require('@catalogServices/ProductService');

describe('LatestProducts', () => {
  test('show loading', () => {
    getLatestProducts.mockReturnValue(new Promise(() => {})); // never resolve

    render(<LatestProducts />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  test('show empty state', async () => {
    getLatestProducts.mockResolvedValue([]);

    render(<LatestProducts />);

    await waitFor(() => {
      expect(screen.getByText(/no products available/i)).toBeInTheDocument();
    });
  });

  test('show product list', async () => {
    getLatestProducts.mockResolvedValue([
      {
        id: 1,
        name: 'Product A',
        slug: 'product-a',
        createdOn: new Date().toISOString(),
      },
    ]);

    render(<LatestProducts />);

    await waitFor(() => {
      expect(screen.getByText('Product A')).toBeInTheDocument();
    });
  });
});