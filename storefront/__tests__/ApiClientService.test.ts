import apiClient from '@/common/services/ApiClientService';

describe('ApiClientService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // mock global fetch
  global.fetch = jest.fn();

  test('GET should call fetch without options', async () => {
    (fetch as jest.Mock).mockResolvedValue({ ok: true });

    await apiClient.get('/test');

    expect(fetch).toHaveBeenCalledWith('/test', undefined);
  });

  test('POST should send JSON body', async () => {
    (fetch as jest.Mock).mockResolvedValue({ ok: true });

    await apiClient.post('/test', JSON.stringify({ a: 1 }));

    expect(fetch).toHaveBeenCalledWith(
      '/test',
      expect.objectContaining({
        method: 'POST',
        headers: {
          'Content-type': 'application/json; charset=UTF-8',
        },
        body: JSON.stringify({ a: 1 }),
      })
    );
  });

  test('PUT should send data', async () => {
    (fetch as jest.Mock).mockResolvedValue({ ok: true });

    await apiClient.put('/test', 'data');

    expect(fetch).toHaveBeenCalledWith(
      '/test',
      expect.objectContaining({
        method: 'PUT',
        body: 'data',
      })
    );
  });

  test('DELETE should call fetch with DELETE method', async () => {
    (fetch as jest.Mock).mockResolvedValue({ ok: true });

    await apiClient.delete('/test');

    expect(fetch).toHaveBeenCalledWith(
      '/test',
      expect.objectContaining({
        method: 'DELETE',
      })
    );
  });

  test('should remove content-type when using FormData', async () => {
    (fetch as jest.Mock).mockResolvedValue({ ok: true });

    const formData = new FormData();
    formData.append('file', 'test');

    await apiClient.post('/upload', formData);

    const callArgs = (fetch as jest.Mock).mock.calls[0][1];

    expect(callArgs.headers['Content-type']).toBeUndefined();
  });

  
  test('should throw error when fetch fails', async () => {
    (fetch as jest.Mock).mockRejectedValue(new Error('Network error'));

    await expect(apiClient.get('/test')).rejects.toThrow('Network error');
  });
});