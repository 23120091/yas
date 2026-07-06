import apiClientService from '@/common/services/ApiClientService';

export async function getStatesOrProvinces(id: number) {
  const response = await apiClientService.get(`/api/location/storefront/state-or-provinces/${id}`);
  if (response.status >= 200 && response.status < 300) {
    return response.json();
  }
  return [];
}
