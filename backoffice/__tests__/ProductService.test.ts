import {
  getLatestProducts,
  getProducts,
  getProduct,
  createProduct,
  updateProduct,
  deleteProduct,
  getVariationsByProductId,
  getRelatedProductByProductId,
  getProductOptionValueByProductId,
} from '@catalogServices/ProductService';

import apiClientService from '@commonServices/ApiClientService';

// mock toàn bộ apiClient
jest.mock('@commonServices/ApiClientService', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  delete: jest.fn(),
}));

describe('ProductService', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  // ✅ getLatestProducts
  test('getLatestProducts success', async () => {
    (apiClientService.get as jest.Mock).mockResolvedValue({
      status: 200,
      json: () => Promise.resolve([{ id: 1 }]),
    });

    const res = await getLatestProducts(5);
    expect(res).toEqual([{ id: 1 }]);
  });

  test('getLatestProducts fail', async () => {
    (apiClientService.get as jest.Mock).mockResolvedValue({
      status: 500,
      statusText: 'Error',
    });

    await expect(getLatestProducts(5)).rejects.toThrow('Error');
  });

  // ✅ getProducts
  test('getProducts', async () => {
    (apiClientService.get as jest.Mock).mockResolvedValue({
      json: () => Promise.resolve({ data: [] }),
    });

    const res = await getProducts(1, 'a', 'b');
    expect(res).toEqual({ data: [] });
  });

  // ✅ getProduct
  test('getProduct', async () => {
    (apiClientService.get as jest.Mock).mockResolvedValue({
      json: () => Promise.resolve({ id: 1 }),
    });

    const res = await getProduct(1);
    expect(res).toEqual({ id: 1 });
  });

  // ✅ createProduct
  test('createProduct', async () => {
    (apiClientService.post as jest.Mock).mockResolvedValue({ ok: true });

    const res = await createProduct({ name: 'test' } as any);
    expect(res).toEqual({ ok: true });
  });

  // ✅ updateProduct (204)
  test('updateProduct return response when 204', async () => {
    (apiClientService.put as jest.Mock).mockResolvedValue({
      status: 204,
    });

    const res = await updateProduct(1, {} as any);
    expect(res.status).toBe(204);
  });

  // ✅ updateProduct (non-204)
  test('updateProduct return json when not 204', async () => {
    (apiClientService.put as jest.Mock).mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ updated: true }),
    });

    const res = await updateProduct(1, {} as any);
    expect(res).toEqual({ updated: true });
  });

  // ✅ deleteProduct (204)
  test('deleteProduct return response when 204', async () => {
    (apiClientService.delete as jest.Mock).mockResolvedValue({
      status: 204,
    });

    const res = await deleteProduct(1);
    expect(res.status).toBe(204);
  });

  // ✅ deleteProduct (non-204)
  test('deleteProduct return json when not 204', async () => {
    (apiClientService.delete as jest.Mock).mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ deleted: true }),
    });

    const res = await deleteProduct(1);
    expect(res).toEqual({ deleted: true });
  });

  // ✅ getVariationsByProductId
  test('getVariationsByProductId success', async () => {
    (apiClientService.get as jest.Mock).mockResolvedValue({
      status: 200,
      json: () => Promise.resolve([{ id: 1 }]),
    });

    const res = await getVariationsByProductId(1);
    expect(res).toEqual([{ id: 1 }]);
  });

  test('getVariationsByProductId fail', async () => {
    (apiClientService.get as jest.Mock).mockResolvedValue({
      status: 500,
      statusText: 'Error',
    });

    await expect(getVariationsByProductId(1)).rejects.toThrow('Error');
  });

  // ✅ getRelatedProductByProductId
  test('getRelatedProductByProductId success', async () => {
    (apiClientService.get as jest.Mock).mockResolvedValue({
      status: 200,
      json: () => Promise.resolve([{ id: 2 }]),
    });

    const res = await getRelatedProductByProductId(1);
    expect(res).toEqual([{ id: 2 }]);
  });

  // ✅ getProductOptionValueByProductId
  test('getProductOptionValueByProductId success', async () => {
    (apiClientService.get as jest.Mock).mockResolvedValue({
      status: 200,
      json: () => Promise.resolve([{ id: 3 }]),
    });

    const res = await getProductOptionValueByProductId(1);
    expect(res).toEqual([{ id: 3 }]);
  });
});