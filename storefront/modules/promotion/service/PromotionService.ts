import apiClientService from '@/common/services/ApiClientService';
import { PromotionVerifyRequest, PromotionVerifyResult } from '../model/Promotion';

export async function verifyPromotion(
  request: PromotionVerifyRequest
): Promise<PromotionVerifyResult> {
  const response = await apiClientService.post(
    '/api/promotion/storefront/promotions/verify',
    JSON.stringify(request)
  );
  if (response.status >= 200 && response.status < 300) {
    return response.json();
  }
  throw new Error(response.statusText);
}
