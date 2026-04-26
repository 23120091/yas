import { render, waitFor } from '@testing-library/react';
import ProductCard from '../common/components/ProductCard';
import * as mediaService from '@/modules/media/services/MediaService';

// mock ProductCardBase để kiểm tra props
jest.mock('../common/components/ProductCardBase', () => {
  return function MockProductCardBase(props: any) {
    return (
      <div data-testid="product-card-base">
        {props.thumbnailUrl}
      </div>
    );
  };
});

// mock API
jest.mock('@/modules/media/services/MediaService', () => ({
  getMediaById: jest.fn(),
}));

describe('ProductCard', () => {
  const mockProduct = {
    id: 1,
    name: 'Test',
    thumbnailUrl: 'default-url',
  };

  test('use product thumbnail when no thumbnailId', () => {
    const { getByTestId } = render(<ProductCard product={mockProduct} />);

    expect(getByTestId('product-card-base').textContent).toBe('default-url');
  });

  test('fetch thumbnail when thumbnailId provided', async () => {
    (mediaService.getMediaById as jest.Mock).mockResolvedValue({
      url: 'fetched-url',
    });

    const { getByTestId } = render(
      <ProductCard product={mockProduct} thumbnailId={123} />
    );

    await waitFor(() => {
      expect(getByTestId('product-card-base').textContent).toBe('fetched-url');
    });
  });

  test('handle fetch error gracefully', async () => {
    (mediaService.getMediaById as jest.Mock).mockRejectedValue(
      new Error('fail')
    );

    const { getByTestId } = render(
      <ProductCard product={mockProduct} thumbnailId={123} />
    );

    await waitFor(() => {
      // vẫn giữ giá trị ban đầu (empty string)
      expect(getByTestId('product-card-base').textContent).toBe('');
    });
  });
});