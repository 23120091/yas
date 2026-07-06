import apiClientService from '@/common/services/ApiClientService';

export async function getDistricts(id: number) {
  const response = await apiClientService.get(`/api/location/storefront/district/${id}`);
  if (response.status >= 200 && response.status < 300) {
    return response.json();
  }
  return [];
}
