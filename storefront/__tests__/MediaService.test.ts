import { getMediaById } from '@/modules/media/services/MediaService';
import apiClient from '@/common/services/ApiClientService';

jest.mock('@/common/services/ApiClientService', () => ({
  get: jest.fn(),
}));

describe('MediaService - getMediaById', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  test('should return media when status is 200', async () => {
    const mockResponse = {
      status: 200,
      json: async () => ({ id: 1, url: 'image.jpg' }),
    };

    (apiClient.get as jest.Mock).mockResolvedValue(mockResponse);

    const result = await getMediaById(1);

    expect(result).toEqual({ id: 1, url: 'image.jpg' });
    expect(apiClient.get).toHaveBeenCalledWith('/api/media/medias/1');
  });

  test('should return media when status is 201 (edge case)', async () => {
    const mockResponse = {
      status: 201,
      json: async () => ({ id: 2, url: 'new.jpg' }),
    };

    (apiClient.get as jest.Mock).mockResolvedValue(mockResponse);

    const result = await getMediaById(2);

    expect(result).toEqual({ id: 2, url: 'new.jpg' });
  });

  test('should throw response when status is not 2xx', async () => {
    const mockResponse = {
      status: 500,
    };

    (apiClient.get as jest.Mock).mockResolvedValue(mockResponse);

    await expect(getMediaById(1)).rejects.toEqual(mockResponse);
  });
});