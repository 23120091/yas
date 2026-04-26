import * as service from '@/modules/catalog/services/ProductService';
import apiClient from '@/common/services/ApiClientService';
import { YasError } from '@/common/services/errors/YasError';

jest.mock('@/common/services/ApiClientService', () => ({
  get: jest.fn(),
}));

describe('Storefront ProductService', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  // ✅ 1. simple success case
  test('getFeaturedProducts returns data', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({
      json: async () => ({ data: [] }),
    });

    const res = await service.getFeaturedProducts(1);
    expect(res).toEqual({ data: [] });
  });

  // ✅ 2. branch: success vs error (status check)
  test('getProductOptionValues success', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({
      status: 200,
      json: async () => ['ok'],
    });

    const res = await service.getProductOptionValues(1);
    expect(res).toEqual(['ok']);
  });

  test('getProductOptionValues fail', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({
      status: 500,
      json: async () => 'error',
    });

    await expect(service.getProductOptionValues(1)).rejects.toThrow();
  });

  // ✅ 3. test variation API
  test('getProductVariationsByParentId success', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({
      status: 200,
      json: async () => ['variation'],
    });

    const res = await service.getProductVariationsByParentId(1);
    expect(res).toEqual(['variation']);
  });

  // ✅ 4. test YasError branch (QUAN TRỌNG)
  test('getProductsByIds throws YasError when not ok', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({
      ok: false,
      json: async () => ({ detail: 'fail' }),
    });

    await expect(service.getProductsByIds([1])).rejects.toBeInstanceOf(YasError);
  });

  test('getProductsByIds success', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({
      ok: true,
      json: async () => [{ id: 1 }],
    });

    const res = await service.getProductsByIds([1]);
    expect(res).toEqual([{ id: 1 }]);
  });

  // ✅ 5. test reject branch
  test('getProductOptionValueByProductId fail', async () => {
    (apiClient.get as jest.Mock).mockResolvedValue({
      status: 500,
      statusText: 'Server error',
    });

    await expect(
      service.getProductOptionValueByProductId(1)
    ).rejects.toThrow('Server error');
  });
});